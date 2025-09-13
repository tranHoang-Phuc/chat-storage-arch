package com.sds.phucth.chatstoragearch.services;

import com.sds.phucth.chatstoragearch.consts.ConversationConstants;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class ConversationSequenceService {
    StringRedisTemplate redis;

    public long nextSeq(String conversationId) {
        String key = ConversationConstants.Seq.NEXT_SEQ_FORMAT.formatted(conversationId);
        return redis.opsForValue().increment(key);
    }
}
