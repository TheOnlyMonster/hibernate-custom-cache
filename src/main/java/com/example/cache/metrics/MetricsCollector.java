package com.example.cache.metrics;

import java.util.concurrent.atomic.AtomicLong;

public class MetricsCollector {
    private final AtomicLong hits = new AtomicLong();
    private final AtomicLong misses = new AtomicLong();
    private final AtomicLong puts = new AtomicLong();
    private final AtomicLong evictions = new AtomicLong();

    public void hit() { hits.incrementAndGet(); }
    public void miss() { misses.incrementAndGet(); }
    public void put() { puts.incrementAndGet(); }
    public void evict() { evictions.incrementAndGet(); }

    public long getHits() { return hits.get(); }
    public long getMisses() { return misses.get(); }
    public long getPuts() { return puts.get(); }
    public long getEvictions() { return evictions.get(); }

    public String summary() {
        return String.format("hits=%d misses=%d puts=%d evictions=%d",
                getHits(), getMisses(), getPuts(), getEvictions());
    }
}
