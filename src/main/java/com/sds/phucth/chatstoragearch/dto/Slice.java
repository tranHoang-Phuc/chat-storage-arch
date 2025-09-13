package com.sds.phucth.chatstoragearch.dto;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Slice {
    long start;
    int length;
    String messageId;
}
