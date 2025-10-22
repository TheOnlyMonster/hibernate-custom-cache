package com.example.cache.utils;



public interface CacheKey {
    static <T extends CacheKey> T convert(Object key, Class<T> type) {
        if (key == null) {
            throw new IllegalArgumentException("Key cannot be null");
        }
        if (type == null) {
            throw new IllegalArgumentException("Type cannot be null");
        }
        if (!type.isInstance(key)) {
            throw new IllegalArgumentException("Expected " + type.getName() + " but got: " + key.getClass().getName());
        }
        return type.cast(key);
    }

    String getTenantId();
}

