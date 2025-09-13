package com.sds.phucth.chatstoragearch.utils;

import com.github.f4b6a3.ulid.UlidCreator;

public final class Ulids {
    public static String newUlid() {
        return UlidCreator.getUlid().toString();
    }


}
