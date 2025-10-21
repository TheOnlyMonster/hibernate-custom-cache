package com.example.cache.region;


import com.example.cache.metrics.MetricsCollector;
import com.example.cache.storage.InMemoryLRUCache;
public class RegionImpl {
    private final String regionName;
    private final InMemoryLRUCache<Object, Object> cache;
    private final MetricsCollector metrics;

    public RegionImpl(String regionName, int maxEntries, long ttlMillis, MetricsCollector metrics) {
        this.regionName = regionName;
        this.metrics = metrics;
        this.cache = new InMemoryLRUCache<>(maxEntries, ttlMillis, metrics);
    }

    public Object get(Object key) {
        return cache.get(key);
    }

    public void put(Object key, Object value) {
        cache.put(key, value);
    }

    public void evict(Object key) {
        cache.remove(key);
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