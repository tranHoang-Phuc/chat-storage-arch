package com.sds.phucth.chatstoragearch.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sds.phucth.chatstoragearch.consts.PrefixConstants;
import com.sds.phucth.chatstoragearch.consts.S3Constants;
import com.sds.phucth.chatstoragearch.dto.ChatRecord;
import com.sds.phucth.chatstoragearch.models.MessageRef;
import com.sds.phucth.chatstoragearch.repository.MessageRefRepository;
import com.sds.phucth.chatstoragearch.utils.*;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class L0WriterService {
    ConversationSequenceService conversationSequenceService;
    IdempotencyService idempotencyService;
    S3Service s3Service;
    MessageRefRepository messageRefRepository;
    ObjectMapper objectMapper;
    KafkaTemplate<String, byte[]> kafkaTemplate;
    StringRedisTemplate redisTemplate;

    @Value("${app.s3.prefix}")
    @NonFinal
    String prefix;

    @Value("${topic.write}")
    @NonFinal
    String writeTopic;
    public Optional<String> checkIfMessageAlreadyExists(String clientMsgId) {
        String key = "seen:" + clientMsgId;
        String msgId = redisTemplate.opsForValue().get(key);
        return Optional.ofNullable(msgId);
    }
    public ChatRecord writeL0(String conversationId, String role, Object body,
                              Map<String, Object> meta, String clientMsgId) throws JsonProcessingException {
        if (clientMsgId != null) {
            // idempotency
            var exist = idempotencyService.already(conversationId);

            if (exist.isPresent()) {
                /* đã ghi trước đó -> load từ db hoặc trả ack "đã nhận"
                   hoặc bắn event relay (optional)
                */

            }
        }
        String messageId = Ulids.newUlid();
        long sequence = conversationSequenceService.nextSeq(conversationId);

        var record = ChatRecord.builder()
                .msgId(messageId)
                .conversationId(conversationId)
                .seq(sequence)
                .role(role)
                .body(body)
                .meta(meta)
                .createdAt(OffsetDateTime.now(ZoneOffset.UTC))
                .build();

        // canonical JSON và hash CAS
        byte[] canonical = CanonicalJson.toCanonicalBytes(record);
        String hash = Hashing.sha256Hex(canonical);
        byte[] zstd = ZstdCodec.compress(canonical, 6);
        String key = S3Objects.casKey(prefix, hash);

        s3Service.putBytes(key, zstd, S3Constants.ContentType.ZSTD);

        // Save xuống db

        MessageRef messageRef = MessageRef.builder()
                .id(messageId)
                .conversationId(conversationId)
                .seq(sequence)
                .role(role)
                .refId(PrefixConstants.Ref.CAS + hash)
                .createdAt(OffsetDateTime.now(ZoneOffset.UTC))
                .meta(meta == null ? null : objectMapper.writeValueAsString(meta))
                .build();

        messageRefRepository.save(messageRef);

        // mark idempotent và emit Kafka cho compaction

        if (clientMsgId != null) {
            idempotencyService.markIfFirst(clientMsgId, messageId);
        }
        kafkaTemplate.send(writeTopic, conversationId, CanonicalJson.toCanonicalBytes(
                Map.of("msgId", messageId, "conversationId", conversationId, "seq", sequence, "hash", hash)
        ));

        return record;
    }
}
