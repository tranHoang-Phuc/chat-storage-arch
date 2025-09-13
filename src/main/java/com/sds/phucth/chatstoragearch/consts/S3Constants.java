package com.sds.phucth.chatstoragearch.consts;

public interface S3Constants {
    interface S3Objects {
        String CAS_KEY = "%s/cas/sha256/%s/%s.json.zst";
        String SEG_DATA_KEY = "%s/seg/%s/%s/%s/seg-%s.jsonl.zst";
        String SEG_INDEX_KEY = "%s/seg/%s/%s/%s/seg-%s.idx.%s";
    }

    interface Range {
        String BYTE_FORMAT = "bytes=%d-%d";
    }
    interface ContentType {
        String ZSTD = "application/zstd";
    }
}
