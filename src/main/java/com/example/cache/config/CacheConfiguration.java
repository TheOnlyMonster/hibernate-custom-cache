package com.example.cache.config;

import java.util.Map;

/**
 * Configuration class for the custom cache implementation.
 * Reads configuration from Hibernate properties.
 */
public class CacheConfiguration {
    
    // Configuration property keys
    public static final String MAX_ENTRIES_PROPERTY = "hibernate.cache.max_entries";
    public static final String TTL_SECONDS_PROPERTY = "hibernate.cache.ttl_seconds";
    public static final String LOCK_TIMEOUT_SECONDS_PROPERTY = "hibernate.cache.lock_timeout_seconds";
    public static final String ENABLE_DEBUG_LOGGING_PROPERTY = "hibernate.cache.debug_logging";
    
    // Default values
    private static final int DEFAULT_MAX_ENTRIES = 10000;
    private static final long DEFAULT_TTL_SECONDS = 3600; // 1 hour
    private static final long DEFAULT_LOCK_TIMEOUT_SECONDS = 60; // 1 minute
    private static final boolean DEFAULT_DEBUG_LOGGING = false;
    
    private final int maxEntries;
    private final long ttlMillis;
    private final long lockTimeoutMillis;
    private final boolean debugLogging;
    
    public CacheConfiguration(Map<String, Object> configValues) {
        this.maxEntries = getIntProperty(configValues, MAX_ENTRIES_PROPERTY, DEFAULT_MAX_ENTRIES);
        this.ttlMillis = getLongProperty(configValues, TTL_SECONDS_PROPERTY, DEFAULT_TTL_SECONDS) * 1000;
        this.lockTimeoutMillis = getLongProperty(configValues, LOCK_TIMEOUT_SECONDS_PROPERTY, DEFAULT_LOCK_TIMEOUT_SECONDS) * 1000;
        this.debugLogging = getBooleanProperty(configValues, ENABLE_DEBUG_LOGGING_PROPERTY, DEFAULT_DEBUG_LOGGING);
    }
    
    public int getMaxEntries() {
        return maxEntries;
    }
    
    public long getTtlMillis() {
        return ttlMillis;
    }
    
    public long getLockTimeoutMillis() {
        return lockTimeoutMillis;
    }
    
    public boolean isDebugLogging() {
        return debugLogging;
    }
    
    private int getIntProperty(Map<String, Object> configValues, String key, int defaultValue) {
        Object value = configValues.get(key);
        if (value == null || value.toString().isEmpty()) {
            return defaultValue;
        }
        try {
            if (value instanceof Number) {
                Number number = (Number) value;
                if (number.intValue() < 0) {
                    return defaultValue;
                }
                return number.intValue();
            }
            int intValue = Integer.parseInt(value.toString());
            if (intValue < 0) {
                return defaultValue;
            }
            return intValue;
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
    
    private long getLongProperty(Map<String, Object> configValues, String key, long defaultValue) {
        Object value = configValues.get(key);
        if (value == null || value.toString().isEmpty()) {
            return defaultValue;
        }
        try {
            if (value instanceof Number) {
                Number number = (Number) value;
                if (number.longValue() < 0) {
                    return defaultValue;
                }
                return number.longValue();
            }
            long longValue = Long.parseLong(value.toString());
            if (longValue < 0) {
                return defaultValue;
            }
            return longValue;
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
    
    private boolean getBooleanProperty(Map<String, Object> configValues, String key, boolean defaultValue) {
        Object value = configValues.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return Boolean.parseBoolean(value.toString());
    }
    
    @Override
    public String toString() {
        return "CacheConfiguration{" +
                "maxEntries=" + maxEntries +
                ", ttlMillis=" + ttlMillis +
                ", lockTimeoutMillis=" + lockTimeoutMillis +
                ", debugLogging=" + debugLogging +
                '}';
    }
}
