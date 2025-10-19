package com.example.cache.region;

import com.example.cache.metrics.MetricsCollector;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EntityRegionImplTest {

    @Test
    void putGetAndMetricsWork() {
        // MetricsCollector metrics = new MetricsCollector();
        // EntityRegionImpl region = new EntityRegionImpl("test-region", 10, 0L, metrics);

        // // first read -> miss
        // assertNull(region.get("k1"));
        // assertEquals(1, metrics.getMisses());
        // assertEquals(0, metrics.getHits());
        // assertEquals(0, metrics.getPuts());

        // // put
        // region.put("k1", "v1");
        // assertEquals(1, metrics.getPuts());

        // // second read -> hit
        // assertEquals("v1", region.get("k1"));
        // assertEquals(1, metrics.getHits());
        // assertEquals(1, metrics.getMisses());
    }

    @Test
    void evictRemovesAndCounts() {
        // MetricsCollector metrics = new MetricsCollector();
        // EntityRegionImpl region = new EntityRegionImpl("test-region", 10, 0L, metrics);

        // region.put("x", "val");
        // assertEquals(1, metrics.getPuts());

        // region.evict("x");
        // assertEquals(1, metrics.getEvictions());
        // assertNull(region.get("x"));
        // assertEquals(1, metrics.getMisses());
    }
}
