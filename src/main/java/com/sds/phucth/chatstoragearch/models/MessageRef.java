package com.sds.phucth.chatstoragearch.models;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name="messages_ref")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MessageRef {
    @Id
    private String id; // ULID

    @Column(name="conversation_id")
    private String conversationId;

    private long seq;

    private String role; // user|assistant|tool|system

    @Column(name="ref_id")
    private String refId;

    private String provider;

    private String model;

    @Column(name="tokens_in")
    private Integer tokensIn;

    @Column(name="tokens_out")
    private Integer tokensOut;

    @Column(name="cost_usd")
    private BigDecimal costUsd;

    @Column(name="created_at")
    private OffsetDateTime createdAt;

    @Column(name="meta")
    private String meta;
}
