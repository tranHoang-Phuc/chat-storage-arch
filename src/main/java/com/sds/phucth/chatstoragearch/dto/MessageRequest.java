package com.sds.phucth.chatstoragearch.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageRequest {
    @NotNull(message = "Role is required")
    private String role;  // user|assistant|tool|system

    @NotNull(message = "Message body is required")
    private Object body;  // Nội dung message, có thể là text hoặc rich JSON

    private Map<String, Object> meta;  // Metadata kèm theo message

    private String clientMsgId;  // Dùng để đảm bảo tính idempotency
}
