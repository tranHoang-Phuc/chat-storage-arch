package com.sds.phucth.chatstoragearch.utils;

import com.sds.phucth.chatstoragearch.consts.S3Constants;

public class S3Objects {
    public static String casKey(String prefix, String hash) {
        String ab = hash.substring(0,2);
        return S3Constants.S3Objects.CAS_KEY.formatted(prefix, ab, hash);
    }

    public static String segDataKey(String prefix, String tenant, String yyyyMM, String convId, String segUlid) {
        return S3Constants.S3Objects.SEG_DATA_KEY.formatted(prefix, tenant, yyyyMM, convId, segUlid);
    }
    public static String segIndexKey(String prefix, String tenant, String yyyyMM, String convId, String segUlid, boolean parquet) {
        return S3Constants.S3Objects.SEG_INDEX_KEY.formatted(prefix, tenant, yyyyMM, convId, segUlid, parquet ? "parquet":"json");
    }

}
