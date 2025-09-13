package com.sds.phucth.chatstoragearch.utils;

import com.sds.phucth.chatstoragearch.consts.AlgorithmConstants;

import java.security.MessageDigest;

public final class Hashing {
    public static String sha256Hex(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance(AlgorithmConstants.Hashing.SHA_256);
            byte[] d = md.digest(data);
            StringBuilder sb = new StringBuilder(d.length * 2);
            for (byte b : d) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
