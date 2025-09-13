package com.sds.phucth.chatstoragearch.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sds.phucth.chatstoragearch.consts.PrefixConstants;
import com.sds.phucth.chatstoragearch.consts.S3Constants;
import com.sds.phucth.chatstoragearch.dto.IndexEntry;
import com.sds.phucth.chatstoragearch.models.MessageRef;
import com.sds.phucth.chatstoragearch.repository.MessageRefRepository;
import com.sds.phucth.chatstoragearch.utils.S3Objects;
import com.sds.phucth.chatstoragearch.utils.Ulids;
import com.sds.phucth.chatstoragearch.utils.ZstdCodec;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class CompactorService {
    S3Service s3Service;
    MessageRefRepository messageRefRepository;
    ObjectMapper objectMapper;

    @Value("${app.s3.prefix}")
    @NonFinal
    String prefix;

    @Value("${app.compaction.segmentTargetBytes}")
    @NonFinal
    int targetBytes;

    public void compactGroup(String tenant, String yyyyMM, String conversationId, List<MessageRef> batch) throws Exception {
        String segUlid = Ulids.newUlid();
        String dataKey = S3Objects.segDataKey(prefix, tenant, yyyyMM, conversationId, segUlid);
        String idxKey = S3Objects.segIndexKey(prefix, tenant, yyyyMM, conversationId, segUlid, false);

        ByteArrayOutputStream data = new ByteArrayOutputStream(targetBytes + 1024 * 1024);
        List<IndexEntry> idx = new ArrayList<>();

        long offset = 0L;
        for(MessageRef msgRef : batch) {
            // Load L0 CAS
            String hash = msgRef.getRefId().substring(PrefixConstants.Ref.CAS.length());
            byte[] obj = s3Service.getBytes(S3Objects.casKey(prefix, hash));
            byte[] json = ZstdCodec.decompress(obj);

            // mỗi record -> zstd frame riêng
            byte[] frame = ZstdCodec.compress(json, 9);
            data.write(frame);
            idx.add(IndexEntry.builder()
                            .msgId(msgRef.getId())
                            .offset(offset)
                            .length(frame.length)
                    .build());
            offset += frame.length;
        }

        // write segment + index
        s3Service.putBytes(dataKey, data.toByteArray(), S3Constants.ContentType.ZSTD);
        byte[] idxJson = objectMapper.writeValueAsBytes(idx);
        s3Service.putBytes(dataKey, idxJson, S3Constants.ContentType.ZSTD);

        List<String> msgIds = idx.stream()
                .map(IndexEntry::getMsgId)
                .collect(Collectors.toList());
        List<MessageRef> messageRefs = messageRefRepository.findAllById(msgIds);
        Map<String, MessageRef> msgIdToMessageRef = messageRefs.stream()
                .collect(Collectors.toMap(MessageRef::getId, mr -> mr));
        for (IndexEntry e : idx) {
            MessageRef messageRef = msgIdToMessageRef.get(e.getMsgId());
            if (messageRef != null) {
                messageRef.setRefId(PrefixConstants.Ref.MESSAGE_REF.formatted(segUlid, e.getOffset(), e.getLength()));
            }
        }
        messageRefRepository.saveAll(messageRefs);
    }

    @Transactional
    @Scheduled(fixedDelay = 15000)
    public void runPlanner() throws Exception {
        OffsetDateTime cutoff = OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(30);
        List<MessageRef> cand = messageRefRepository.findEligibleForCompaction(cutoff);

        // Group by conversationId, rồi cắt theo targetBytes
        Map<String, List<MessageRef>> byConv = cand.stream()
                .collect(Collectors.groupingBy(MessageRef::getConversationId));

        for (var entry : byConv.entrySet()) {
            String conv = entry.getKey();
            List<MessageRef> list = entry.getValue();
            list.sort(Comparator.comparingLong(MessageRef::getSeq));

            List<MessageRef> bucket = new ArrayList<>();
            int approx = 0;
            for (MessageRef mr : list) {
                bucket.add(mr);
                approx += 2048; // ước lượng mỗi record ~2KB nén
                if (approx >= targetBytes) {
                    compactGroup("default", YearMonth.now(ZoneOffset.UTC).toString(), conv, bucket);
                    bucket = new ArrayList<>(); approx = 0;
                }
            }
            if (!bucket.isEmpty()) {
                compactGroup("default", YearMonth.now(ZoneOffset.UTC).toString(), conv, bucket);
            }
        }
    }
}