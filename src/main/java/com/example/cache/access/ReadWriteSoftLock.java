package com.example.cache.access;

import org.hibernate.cache.spi.access.SoftLock;

public class ReadWriteSoftLock implements SoftLock {
    private final Object oldValue;
    private final Object version;

    public ReadWriteSoftLock(Object oldValue, Object version) {
        this.oldValue = oldValue;
        this.version = version;
    }

    public Object getActualValue() {
        return oldValue;
    }

    public Object getVersion() {
        return version;
    }
}
