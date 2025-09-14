package com.sds.phucth.chatstoragearch.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sds.phucth.chatstoragearch.consts.PrefixConstants;
import com.sds.phucth.chatstoragearch.dto.SegRequest;
import com.sds.phucth.chatstoragearch.dto.Slice;
import com.sds.phucth.chatstoragearch.models.MessageRef;
import com.sds.phucth.chatstoragearch.repository.MessageRefRepository;
import com.sds.phucth.chatstoragearch.utils.S3Objects;
import com.sds.phucth.chatstoragearch.utils.ZstdCodec;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class ReaderService {
    S3Service s3Service;
    MessageRefRepository messageRefRepository;
    ObjectMapper objectMapper;
    StringRedisTemplate redisTemplate;
    
    Executor executor = Executors.newFixedThreadPool(10);

    @Value("${app.s3.prefix}")
    @NonFinal
    String prefix;

    public List<Map<String,Object>> readWindow(String conversationId, long cursor, int limit, boolean asc) throws Exception {
        try {
            if (conversationId == null || conversationId.trim().isEmpty()) {
                throw new IllegalArgumentException("Conversation ID cannot be null or empty");
            }
            if (limit <= 0 || limit > 1000) {
                throw new IllegalArgumentException("Limit must be between 1 and 1000");
            }

            List<MessageRef> refs = asc ?
                    messageRefRepository.pageAsc(conversationId, cursor, limit)
                    : messageRefRepository.pageDesc(conversationId, cursor, limit);

            if (refs.isEmpty()) {
                return Collections.emptyList();
            }

            Map<String, List<MessageRef>> casRefs = new HashMap<>();
            Map<String, SegRequest> segRefs = new HashMap<>();

            for (MessageRef ref : refs) {
                String refId = ref.getRefId();
                if (refId.startsWith(PrefixConstants.Ref.CAS)) {
                    casRefs.computeIfAbsent(PrefixConstants.Ref.CAS_READ, k -> new ArrayList<>()).add(ref);
                } else if (refId.startsWith(PrefixConstants.Ref.SEG)) {
                    parseAndAddSegRef(ref, segRefs);
                }
            }

            CompletableFuture<List<Map<String, Object>>> casFuture = processCasRefs(casRefs);
            CompletableFuture<List<Map<String, Object>>> segFuture = processSegRefs(segRefs);

            List<Map<String, Object>> casResults = casFuture.get();
            List<Map<String, Object>> segResults = segFuture.get();

            return mergeResultsInOrder(refs, casResults, segResults);

        } catch (Exception e) {
            log.error("Error reading window for conversation {}: {}", conversationId, e.getMessage(), e);
            throw new RuntimeException("Failed to read message window", e);
        }
    }

    private void parseAndAddSegRef(MessageRef ref, Map<String, SegRequest> segRefs) {
        try {
            String[] parts = ref.getRefId().split(":");
            if (parts.length != 4) {
                log.warn("Invalid SEG reference format: {}", ref.getRefId());
                return;
            }

            String segUlid = parts[1];
            long offset = Long.parseLong(parts[2]);
            int length = Integer.parseInt(parts[3]);

            segRefs.computeIfAbsent(segUlid, k -> SegRequest.builder()
                    .segUlid(segUlid)
                    .build())
                    .getSlices().add(Slice.builder()
                            .start(offset)
                            .length(length)
                            .messageId(ref.getId())
                            .build());
        } catch (NumberFormatException e) {
            log.warn("Invalid number format in SEG reference: {}", ref.getRefId(), e);
        }
    }

    private CompletableFuture<List<Map<String, Object>>> processCasRefs(Map<String, List<MessageRef>> casRefs) {
        if (!casRefs.containsKey(PrefixConstants.Ref.CAS_READ)) {
            return CompletableFuture.completedFuture(Collections.emptyList());
        }
        List<MessageRef> refs = casRefs.get(PrefixConstants.Ref.CAS_READ);
        return CompletableFuture.supplyAsync(() -> {
            List<Map<String, Object>> results = new ArrayList<>();
            for (MessageRef ref : refs) {
                try {
                    String hash = ref.getRefId().substring(PrefixConstants.Ref.CAS.length()); // <-- fix
                    byte[] comp = s3Service.getBytes(S3Objects.casKey(prefix, hash));
                    byte[] json = ZstdCodec.decompress(comp);
                    Map<String, Object> record = objectMapper.readValue(json, Map.class);
                    results.add(record);
                } catch (Exception e) {
                    log.error("Error processing CAS reference {}: {}", ref.getRefId(), e.getMessage(), e);
                }
            }
            return results;
        }, executor);
    }

    private CompletableFuture<List<Map<String, Object>>> processSegRefs(Map<String, SegRequest> segRefs) {
        if (segRefs.isEmpty()) {
            return CompletableFuture.completedFuture(Collections.emptyList());
        }

        return CompletableFuture.supplyAsync(() -> {
            List<Map<String, Object>> results = new ArrayList<>();
            for (SegRequest segRequest : segRefs.values()) {
                try {
                    results.addAll(processSegRequest(segRequest));
                } catch (Exception e) {
                    log.error("Error processing SEG request for {}: {}", segRequest.getSegUlid(), e.getMessage(), e);
                }
            }
            return results;
        }, executor);
    }

    private List<Map<String, Object>> processSegRequest(SegRequest segRequest) throws Exception {
        List<Slice> slices = segRequest.getSlices().stream()
                .sorted(Comparator.comparingLong(Slice::getStart))
                .collect(Collectors.toList());

        List<long[]> mergedRanges = mergeConsecutiveRanges(slices);

        String dataKey = resolveSegKeyFromRedis(segRequest.getSegUlid());

        Map<Long, byte[]> blockCache = new HashMap<>();
        List<CompletableFuture<Void>> fetchTasks = mergedRanges.stream()
                .map(range -> CompletableFuture.runAsync(() -> {
                    try {
                        byte[] block = s3Service.rangeGet(dataKey, range[0], range[1]);
                        blockCache.put(range[0], block);
                    } catch (Exception e) {
                        log.error("Error fetching range [{}, {}] for segment {}: {}", 
                                range[0], range[1], segRequest.getSegUlid(), e.getMessage(), e);
                    }
                }, executor))
                .collect(Collectors.toList());

        CompletableFuture.allOf(fetchTasks.toArray(new CompletableFuture[0])).join();

        List<Map<String, Object>> results = new ArrayList<>();
        for (Slice slice : slices) {
            try {
                byte[] block = findContainingBlock(slice, mergedRanges, blockCache);
                if (block != null) {
                    int offsetInBlock = (int) (slice.getStart() - findBlockStart(slice, mergedRanges));
                    byte[] frame = Arrays.copyOfRange(block, offsetInBlock, offsetInBlock + slice.getLength());
                    byte[] json = ZstdCodec.decompress(frame);
                    @SuppressWarnings("unchecked")
                    Map<String, Object> record = objectMapper.readValue(json, Map.class);
                    results.add(record);
                }
            } catch (Exception e) {
                log.error("Error processing slice {}: {}", slice.getMessageId(), e.getMessage(), e);
            }
        }

        return results;
    }

    private List<long[]> mergeConsecutiveRanges(List<Slice> slices) {
        List<long[]> merged = new ArrayList<>();
        long curStart = -1, curEnd = -1;

        for (Slice slice : slices) {
            long sliceStart = slice.getStart();
            long sliceEnd = slice.getStart() + slice.getLength() - 1;

            if (curStart == -1) {
                curStart = sliceStart;
                curEnd = sliceEnd;
            } else if (sliceStart <= curEnd + 1) {
                curEnd = Math.max(curEnd, sliceEnd);
            } else {
                merged.add(new long[]{curStart, curEnd});
                curStart = sliceStart;
                curEnd = sliceEnd;
            }
        }

        if (curStart != -1) {
            merged.add(new long[]{curStart, curEnd});
        }

        return merged;
    }

    private byte[] findContainingBlock(Slice slice, List<long[]> mergedRanges, Map<Long, byte[]> blockCache) {
        for (long[] range : mergedRanges) {
            if (slice.getStart() >= range[0] && (slice.getStart() + slice.getLength() - 1) <= range[1]) {
                return blockCache.get(range[0]);
            }
        }
        return null;
    }

    private long findBlockStart(Slice slice, List<long[]> mergedRanges) {
        for (long[] range : mergedRanges) {
            if (slice.getStart() >= range[0] && (slice.getStart() + slice.getLength() - 1) <= range[1]) {
                return range[0];
            }
        }
        return 0;
    }

    private List<Map<String, Object>> mergeResultsInOrder(
            List<MessageRef> originalRefs,
            List<Map<String, Object>> casResults,
            List<Map<String, Object>> segResults) {

        Map<String, Map<String, Object>> byMsgId = new HashMap<>();
        for (Map<String, Object> r : casResults) {
            Object id = r.get("msgId");
            if (id != null) byMsgId.put(id.toString(), r);
        }
        for (Map<String, Object> r : segResults) {
            Object id = r.get("msgId");
            if (id != null) byMsgId.put(id.toString(), r);
        }

        List<Map<String, Object>> ordered = new ArrayList<>(originalRefs.size());
        for (MessageRef ref : originalRefs) {
            Map<String, Object> r = byMsgId.get(ref.getId());
            if (r != null) ordered.add(r);
        }
        return ordered;
    }

    private String resolveSegKeyFromRedis(String segUlid) {
        String redisKey = "segKey:" + segUlid;
        String s3Key = redisTemplate.opsForValue().get(redisKey);
        if (s3Key == null || s3Key.isBlank()) {
            throw new IllegalStateException("Missing Redis mapping for " + redisKey + " (compactor must set it)");
        }
        return s3Key;
    }
    
    private String constructSegKeyFromUlid(String segUlid) {
        return String.format("%s/seg/unknown/unknown/unknown/seg-%s.jsonl.zst", prefix, segUlid);
    }
}
