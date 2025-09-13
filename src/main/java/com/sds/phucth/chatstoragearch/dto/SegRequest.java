package com.sds.phucth.chatstoragearch.dto;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class SegRequest {
    String segUlid;
    List<Slice> slices = new ArrayList<>();
}
