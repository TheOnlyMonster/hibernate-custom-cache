package com.example.cache.region;

import com.example.cache.metrics.MetricsCollector;
import com.example.cache.storage.InMemoryLRUCache;

public class EntityRegionImpl {
    private final String regionName;
    private final InMemoryLRUCache<CacheKey, Object> cache;
    private final MetricsCollector metrics;

    public EntityRegionImpl(String regionName, int maxEntries, long ttlMillis, MetricsCollector metrics) {
        this.regionName = regionName;
        this.cache = new InMemoryLRUCache<>(maxEntries, ttlMillis);
        this.metrics = metrics;
    }

    public Object get(CacheKey key) {
        Object value = cache.get(key);
        if (value == null) {
            metrics.miss();
        } else {
            metrics.hit();
        }
        return value;
    }

    public void put(CacheKey key, Object value) {
        cache.put(key, value);
        metrics.put();
    }

    public void evict(CacheKey key) {
        cache.remove(key);
        metrics.evict();
    }

    public void evictAll() {
        cache.clear();
    }

    public String getRegionName() {
        return regionName;
    }

    public MetricsCollector getMetrics() {
        return metrics;
    }
}