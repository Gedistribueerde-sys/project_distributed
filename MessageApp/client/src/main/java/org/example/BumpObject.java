package org.example;

import javax.crypto.SecretKey;

public class BumpObject {
    private final long idx;
    private final String tag;
    private final SecretKey key;

    public BumpObject(long idx, String tag, SecretKey key) {
        this.idx = idx;
        this.tag = tag;
        this.key = key;
    }

    public long getIdx() { return idx; }
    public String getTag() { return tag; }
    public SecretKey getKey() { return key; }
}
