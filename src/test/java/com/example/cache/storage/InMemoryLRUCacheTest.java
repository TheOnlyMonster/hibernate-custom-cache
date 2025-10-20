package com.example.cache.storage;

import com.example.cache.metrics.MetricsCollector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("InMemoryLRUCache Tests")
class InMemoryLRUCacheTest {

    private InMemoryLRUCache<String, String> cache;
    private MetricsCollector metrics;

    @BeforeEach
    void setUp() {
        metrics = new MetricsCollector();
        cache = new InMemoryLRUCache<>(3, 1000, metrics); // max 3 entries, 1 second TTL
    }

    @Test
    @DisplayName("Should return null for non-existent key")
    void testGetNonExistent() {
        assertNull(cache.get("nonexistent"));
        assertEquals(1, metrics.getMisses());
        assertEquals(0, metrics.getHits());
    }

    @Test
    @DisplayName("Should store and retrieve value")
    void testPutAndGet() {
        cache.put("key1", "value1");
        assertEquals("value1", cache.get("key1"));
        assertEquals(1, metrics.getPuts());
        assertEquals(1, metrics.getHits());
    }

    @Test
    @DisplayName("Should update existing value")
    void testUpdateExisting() {
        cache.put("key1", "value1");
        cache.put("key1", "value2");
        
        assertEquals("value2", cache.get("key1"));
        assertEquals(2, metrics.getPuts());
        assertEquals(1, cache.size());
    }

    @Test
    @DisplayName("Should evict LRU item when capacity exceeded")
    void testLRUEviction() {
        cache.put("key1", "value1");
        cache.put("key2", "value2");
        cache.put("key3", "value3");
        
        // All should be present
        assertNotNull(cache.get("key1"));
        assertNotNull(cache.get("key2"));
        assertNotNull(cache.get("key3"));
        
        // Add 4th item, key3 was accessed most recently, so key1 should be evicted
        cache.put("key4", "value4");
        
        assertNull(cache.get("key1")); // Evicted
        assertNotNull(cache.get("key2")); // Still present
        assertNotNull(cache.get("key3")); // Still present
        assertNotNull(cache.get("key4")); // Just added
        
        assertEquals(1, metrics.getEvictions());
        assertEquals(3, cache.size());
    }

    @Test
    @DisplayName("Should respect LRU order based on access")
    void testLRUOrdering() {
        cache.put("key1", "value1");
        cache.put("key2", "value2");
        cache.put("key3", "value3");
        
        // Access key1 to make it most recently used
        cache.get("key1");
        
        // key2 is now LRU (least recently used)
        cache.put("key4", "value4");
        
        assertNotNull(cache.get("key1")); // Accessed, should remain
        assertNull(cache.get("key2"));    // LRU, should be evicted
        assertNotNull(cache.get("key3"));
        assertNotNull(cache.get("key4"));
    }

    @Test
    @DisplayName("Should remove item")
    void testRemove() {
        cache.put("key1", "value1");
        cache.remove("key1");
        
        assertNull(cache.get("key1"));
        assertEquals(0, cache.size());
    }

    @Test
    @DisplayName("Should clear all items")
    void testClear() {
        cache.put("key1", "value1");
        cache.put("key2", "value2");
        
        cache.clear();
        
        assertNull(cache.get("key1"));
        assertNull(cache.get("key2"));
        assertEquals(0, cache.size());
    }

    @Test
    @DisplayName("Should expire entries after TTL")
    void testTTLExpiration() throws InterruptedException {
        cache.put("key1", "value1");
        
        // Should be accessible immediately
        assertNotNull(cache.get("key1"));
        
        // Wait for TTL to expire (1 second + buffer)
        Thread.sleep(1100);
        
        // Should be expired and return null
        assertNull(cache.get("key1"));
        assertEquals(0, cache.size()); // Should be removed from cache
    }

    @Test
    @DisplayName("Should handle concurrent access safely")
    void testConcurrentAccess() throws InterruptedException {
        int threadCount = 10;
        int operationsPerThread = 100;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    for (int j = 0; j < operationsPerThread; j++) {
                        String key = "key-" + (threadId % 5); // Use 5 different keys
                        String value = "value-" + threadId + "-" + j;
                        cache.put(key, value);
                        cache.get(key);
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        executor.shutdown();
        
        // Should not crash and should maintain reasonable size
        assertTrue(cache.size() <= 3); // Max capacity is 3
    }

    @Test
    @DisplayName("Should track metrics correctly")
    void testMetrics() {
        cache.put("key1", "value1");
        cache.put("key2", "value2");
        
        cache.get("key1"); // Hit
        cache.get("key3"); // Miss
        cache.get("key1"); // Hit
        
        cache.put("key3", "value3");
        cache.put("key4", "value4"); // Should trigger eviction
        
        assertEquals(4, metrics.getPuts());
        assertEquals(2, metrics.getHits());
        assertEquals(1, metrics.getMisses());
        assertEquals(1, metrics.getEvictions());
    }

    @Test
    @DisplayName("Should handle null keys gracefully")
    void testNullKey() {
        assertThrows(NullPointerException.class, () -> cache.put(null, "value"));
        assertThrows(NullPointerException.class, () -> cache.get(null));
    }

    @Test
    @DisplayName("Should handle null values")
    void testNullValue() {
        cache.put("key1", null);
        assertNull(cache.get("key1"));
        // Null values are stored, so it's a hit, not a miss
        assertEquals(1, metrics.getHits());
    }

    @Test
    @DisplayName("Should validate constructor parameters")
    void testInvalidConstructorParams() {
        assertThrows(IllegalArgumentException.class, 
            () -> new InMemoryLRUCache<String, String>(0, 1000, metrics));
        assertThrows(IllegalArgumentException.class, 
            () -> new InMemoryLRUCache<String, String>(-1, 1000, metrics));
        assertThrows(IllegalArgumentException.class, 
            () -> new InMemoryLRUCache<String, String>(10, -1, metrics));
    }

    @Test
    @DisplayName("Should handle zero TTL (no expiration)")
    void testZeroTTL() throws InterruptedException {
        InMemoryLRUCache<String, String> noExpiryCache = 
            new InMemoryLRUCache<>(3, 0, metrics);
        
        noExpiryCache.put("key1", "value1");
        Thread.sleep(100); // Wait a bit
        
        // Should still be present with 0 TTL
        assertNotNull(noExpiryCache.get("key1"));
    }
}