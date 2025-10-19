package com.example.cache.utils;



public interface CustomUtils {
    static <T extends CacheKey> T toCacheKey(Object key, Class<T> type) {
        if (key == null) {
            throw new IllegalArgumentException("Key cannot be null");
        }
        if (!type.isInstance(key)) {
            throw new IllegalArgumentException("Expected " + type.getName() + " but got: " + key.getClass().getName());
        }
        return type.cast(key);
    }
}

