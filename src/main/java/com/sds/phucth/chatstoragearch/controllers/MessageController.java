package com.sds.phucth.chatstoragearch.controllers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.sds.phucth.chatstoragearch.dto.ChatRecord;
import com.sds.phucth.chatstoragearch.dto.MessageRequest;
import com.sds.phucth.chatstoragearch.services.L0WriterService;
import com.sds.phucth.chatstoragearch.services.ReaderService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/conversations/{conversationId}/messages")
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
@RequiredArgsConstructor
public class MessageController {
    L0WriterService l0WriterService;
    ReaderService readerService;

    @Value("${app.redis.idempotencyTtlSeconds}")
    @NonFinal
    private int idempotencyTtlSeconds;

    @PostMapping
    public ResponseEntity<Map<String, Object>> saveMessage(
            @PathVariable String conversationId,
            @RequestBody @Valid MessageRequest messageRequest) throws JsonProcessingException {

        // Validate idempotency (kiểm tra nếu message đã tồn tại)
        if (messageRequest.getClientMsgId() != null) {
            Optional<String> existingMsg = l0WriterService.checkIfMessageAlreadyExists(messageRequest.getClientMsgId());
            if (existingMsg.isPresent()) {
                return ResponseEntity.status(HttpStatus.OK)
                        .body(Map.of("msgId", existingMsg.get(), "status", "already exists"));
            }
        }

        // Ghi message vào hệ thống
        ChatRecord chatRecord = l0WriterService.writeL0(
                conversationId,
                messageRequest.getRole(),
                messageRequest.getBody(),
                messageRequest.getMeta(),
                messageRequest.getClientMsgId()
        );

        // Trả về thông tin message đã được ghi
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("msgId", chatRecord.getMsgId(), "seq", chatRecord.getSeq(), "status", "success"));
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> getMessages(
            @PathVariable String conversationId,
            @RequestParam(defaultValue = "0") long cursor,
            @RequestParam(defaultValue = "50") @Min(1) @Max(200) int limit,
            @RequestParam(defaultValue = "asc") @Pattern(regexp="(?i)asc|desc") String order) {

        boolean ascending = order.equalsIgnoreCase("asc");

        // Đọc và trả về message
        try {
            List<Map<String, Object>> messages = readerService.readWindow(conversationId, cursor, limit, ascending);
            return ResponseEntity.ok(messages);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Collections.singletonList(Map.of("error", "Failed to read messages")));
        }
    }
}
