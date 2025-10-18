package com.example.cache.access;

import org.hibernate.cache.spi.access.SoftLock;

public class ReadWriteSoftLock implements SoftLock {
    private final Object key;
    private final Object oldValue;
    private final Object version;
    private final long timestamp;

    public ReadWriteSoftLock(Object key, Object oldValue, Object version) {
        this.key = key;
        this.oldValue = oldValue;
        this.version = version;
        this.timestamp = System.currentTimeMillis();
    }

    public Object getKey() {
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