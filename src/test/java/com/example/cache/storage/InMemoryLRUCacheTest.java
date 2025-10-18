package com.example.cache.storage;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class InMemoryLRUCacheTest {

    @Test
    void putAndGetWorks() {
        InMemoryLRUCache<String, String> cache = new InMemoryLRUCache<>(3, 0);
        cache.put("a","1");
        cache.put("b","2");
        cache.put("c","3");

        assertEquals("1", cache.get("a"));
        assertEquals("2", cache.get("b"));
        assertEquals("3", cache.get("c"));
    }

    @Test
    void evictionOrderIsLRU() {
        InMemoryLRUCache<String, String> cache = new InMemoryLRUCache<>(2, 0);
        cache.put("a","1");
        cache.put("b","2");
        // access a to make it MRU
        cache.get("a");
        cache.put("c","3"); // should evict b
        assertNotNull(cache.get("a"));
        assertNull(cache.get("b"));
        assertNotNull(cache.get("c"));
    }

    @Test
    void ttlExpiresEntry() throws Exception {
        InMemoryLRUCache<String, String> cache = new InMemoryLRUCache<>(10, 100);
        cache.put("x","v");
        Thread.sleep(150);
        assertNull(cache.get("x"));
    }
}
