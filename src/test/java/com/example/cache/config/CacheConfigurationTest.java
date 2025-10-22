package com.example.cache.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Cache Configuration Tests")
class CacheConfigurationTest {

    private Map<String, Object> configValues;

    @BeforeEach
    void setUp() {
        configValues = new HashMap<>();
    }

    @Test
    @DisplayName("Should use default values when no configuration provided")
    void testDefaultValues() {
        CacheConfiguration config = new CacheConfiguration(configValues);

        assertEquals(10000, config.getMaxEntries());
        assertEquals(3600 * 1000, config.getTtlMillis());
        assertEquals(60 * 1000, config.getLockTimeoutMillis());
        assertFalse(config.isDebugLogging());
    }

    @Test
    @DisplayName("Should use provided configuration values")
    void testProvidedValues() {
        configValues.put("hibernate.cache.max_entries", 50000);
        configValues.put("hibernate.cache.ttl_seconds", 1800);
        configValues.put("hibernate.cache.lock_timeout_seconds", 30);
        configValues.put("hibernate.cache.debug_logging", true);

        CacheConfiguration config = new CacheConfiguration(configValues);

        assertEquals(50000, config.getMaxEntries());
        assertEquals(1800 * 1000, config.getTtlMillis());
        assertEquals(30 * 1000, config.getLockTimeoutMillis());
        assertTrue(config.isDebugLogging());
    }

    @Test
    @DisplayName("Should handle string configuration values")
    void testStringValues() {
        configValues.put("hibernate.cache.max_entries", "25000");
        configValues.put("hibernate.cache.ttl_seconds", "7200");
        configValues.put("hibernate.cache.lock_timeout_seconds", "120");
        configValues.put("hibernate.cache.debug_logging", "true");

        CacheConfiguration config = new CacheConfiguration(configValues);

        assertEquals(25000, config.getMaxEntries());
        assertEquals(7200 * 1000, config.getTtlMillis());
        assertEquals(120 * 1000, config.getLockTimeoutMillis());
        assertTrue(config.isDebugLogging());
    }

    @Test
    @DisplayName("Should handle mixed configuration types")
    void testMixedTypes() {
        configValues.put("hibernate.cache.max_entries", 30000); // Integer
        configValues.put("hibernate.cache.ttl_seconds", "900"); // String
        configValues.put("hibernate.cache.lock_timeout_seconds", 45L); // Long
        configValues.put("hibernate.cache.debug_logging", Boolean.TRUE); // Boolean

        CacheConfiguration config = new CacheConfiguration(configValues);

        assertEquals(30000, config.getMaxEntries());
        assertEquals(900 * 1000, config.getTtlMillis());
        assertEquals(45 * 1000, config.getLockTimeoutMillis());
        assertTrue(config.isDebugLogging());
    }

    @Test
    @DisplayName("Should handle invalid string values gracefully")
    void testInvalidStringValues() {
        configValues.put("hibernate.cache.max_entries", "invalid");
        configValues.put("hibernate.cache.ttl_seconds", "not_a_number");
        configValues.put("hibernate.cache.lock_timeout_seconds", "also_invalid");
        configValues.put("hibernate.cache.debug_logging", "maybe");

        CacheConfiguration config = new CacheConfiguration(configValues);

        // Should fall back to defaults
        assertEquals(10000, config.getMaxEntries());
        assertEquals(3600 * 1000, config.getTtlMillis());
        assertEquals(60 * 1000, config.getLockTimeoutMillis());
        assertFalse(config.isDebugLogging());
    }

    @Test
    @DisplayName("Should handle null values gracefully")
    void testNullValues() {
        configValues.put("hibernate.cache.max_entries", null);
        configValues.put("hibernate.cache.ttl_seconds", null);
        configValues.put("hibernate.cache.lock_timeout_seconds", null);
        configValues.put("hibernate.cache.debug_logging", null);

        CacheConfiguration config = new CacheConfiguration(configValues);

        // Should use defaults
        assertEquals(10000, config.getMaxEntries());
        assertEquals(3600 * 1000, config.getTtlMillis());
        assertEquals(60 * 1000, config.getLockTimeoutMillis());
        assertFalse(config.isDebugLogging());
    }

    @Test
    @DisplayName("Should handle zero TTL correctly")
    void testZeroTTL() {
        configValues.put("hibernate.cache.ttl_seconds", 0);

        CacheConfiguration config = new CacheConfiguration(configValues);

        assertEquals(0, config.getTtlMillis());
    }

    @Test
    @DisplayName("Should handle negative values gracefully")
    void testNegativeValues() {
        configValues.put("hibernate.cache.max_entries", -100);
        configValues.put("hibernate.cache.ttl_seconds", -50);
        configValues.put("hibernate.cache.lock_timeout_seconds", -10);

        CacheConfiguration config = new CacheConfiguration(configValues);

        // Should use defaults for negative values
        assertEquals(10000, config.getMaxEntries());
        assertEquals(3600 * 1000, config.getTtlMillis());
        assertEquals(60 * 1000, config.getLockTimeoutMillis());
    }

    @Test
    @DisplayName("Should provide meaningful toString representation")
    void testToString() {
        configValues.put("hibernate.cache.max_entries", 15000);
        configValues.put("hibernate.cache.ttl_seconds", 1800);
        configValues.put("hibernate.cache.lock_timeout_seconds", 90);
        configValues.put("hibernate.cache.debug_logging", true);

        CacheConfiguration config = new CacheConfiguration(configValues);
        String toString = config.toString();

        assertTrue(toString.contains("maxEntries=15000"));
        assertTrue(toString.contains("ttlMillis=1800000"));
        assertTrue(toString.contains("lockTimeoutMillis=90000"));
        assertTrue(toString.contains("debugLogging=true"));
    }

    @Test
    @DisplayName("Should handle very large values")
    void testLargeValues() {
        configValues.put("hibernate.cache.max_entries", Integer.MAX_VALUE);
        configValues.put("hibernate.cache.ttl_seconds", Long.MAX_VALUE / 1000);
        configValues.put("hibernate.cache.lock_timeout_seconds", Integer.MAX_VALUE);

        CacheConfiguration config = new CacheConfiguration(configValues);

        assertEquals(Integer.MAX_VALUE, config.getMaxEntries());
        assertEquals(Long.MAX_VALUE / 1000 * 1000, config.getTtlMillis());
        assertEquals((long) Integer.MAX_VALUE * 1000, config.getLockTimeoutMillis());
    }
}
