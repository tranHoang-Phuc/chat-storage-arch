package com.sds.phucth.chatstoragearch.dto;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;

import java.time.OffsetDateTime;
import java.util.Map;

@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ChatRecord {
    String msgId;
    String conversationId;
    long seq;
    String role;          // user|assistant|tool|system
    Object body;          // text / rich JSON tùy bạn
    Map<String,Object> meta;
    OffsetDateTime createdAt;
}