package com.sds.phucth.chatstoragearch.utils;

import com.github.luben.zstd.Zstd;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

public final class ZstdCodec {
    public static byte[] compress(byte[] input, int level) {
        return Zstd.compress(input, level);
    }

    public static byte[] decompress(byte[] zstdFrame) {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(zstdFrame);
             com.github.luben.zstd.ZstdInputStream zin = new com.github.luben.zstd.ZstdInputStream(bais);
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            zin.transferTo(baos);
            return baos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
