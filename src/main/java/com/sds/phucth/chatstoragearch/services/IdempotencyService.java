package com.sds.phucth.chatstoragearch.services;

import com.sds.phucth.chatstoragearch.consts.IdempotencyConstants;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class IdempotencyService {

    StringRedisTemplate redis;

    @Value("${app.redis.idempotencyTtlSeconds}")
    @NonFinal
    int ttl;

    public boolean markIfFirst(String clientMsgId, String msgId) {
        String key = IdempotencyConstants.KeyFormat.SEEN.formatted(clientMsgId);
        Boolean ok = redis.opsForValue().setIfAbsent(key, msgId, Duration.ofSeconds(ttl));
        return Boolean.TRUE.equals(ok);
    }

    public Optional<String> already(String clientMsgId) {
        String v = redis.opsForValue().get(IdempotencyConstants.KeyFormat.SEEN.formatted(clientMsgId));
        return Optional.ofNullable(v);
    }
}
