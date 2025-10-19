package com.example.cache.access;

import org.hibernate.cache.spi.access.SoftLock;

import com.example.cache.utils.CacheKey;

public class ReadWriteSoftLock implements SoftLock {
    private final CacheKey key;
    private final Object oldValue;
    private final Object version;
    private final long timestamp;

    public ReadWriteSoftLock(CacheKey key, Object oldValue, Object version) {
        this.key = key;
        this.oldValue = oldValue;
        this.version = version;
        this.timestamp = System.currentTimeMillis();
    }

    public CacheKey getKey() {
        return key;
    }

    public Object getOldValue() {
        return oldValue;
    }

    public Object getVersion() {
        return version;
    }

    public long getTimestamp() {
        return timestamp;
    }
}