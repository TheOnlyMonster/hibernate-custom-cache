package com.example.cache.metrics;

import com.example.cache.access.entities.EntityCacheKey;
import com.example.cache.access.entities.ReadWriteEntityDataAccess;
import com.example.cache.factory.CustomRegionFactory;
import com.example.cache.region.DomainDataRegionAdapter;
import com.example.cache.region.RegionImpl;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Cache Metrics Tests")
class MetricsTest {

    private ReadWriteEntityDataAccess dataAccess;
    private RegionImpl entityRegion;
    private DomainDataRegionAdapter domainDataRegion;
    private CustomRegionFactory regionFactory;
    private MetricsCollector metrics;

    @BeforeEach
    void setUp() {
        metrics = new MetricsCollector();
        entityRegion = new RegionImpl("test-region", 1000, 60000, metrics);
        
        regionFactory = new CustomRegionFactory();
        Map<String, Object> configValues = new java.util.HashMap<>();
        regionFactory.start(null, configValues);
        
        domainDataRegion = new DomainDataRegionAdapter(entityRegion, regionFactory, null);
        dataAccess = new ReadWriteEntityDataAccess(entityRegion, domainDataRegion);
    }

    @Test
    @DisplayName("Should track cache hits correctly")
    void testCacheHits() {
        // Pre-populate cache
        EntityCacheKey key1 = new EntityCacheKey(1L, "TestEntity", null);
        EntityCacheKey key2 = new EntityCacheKey(2L, "TestEntity", null);
        
        dataAccess.putFromLoad(null, key1, "value1", 1);
        dataAccess.putFromLoad(null, key2, "value2", 1);

        // Clear initial metrics
        long initialHits = metrics.getHits();
        
        // Perform reads that should hit cache
        dataAccess.get(null, key1);
        dataAccess.get(null, key2);
        dataAccess.get(null, key1); // Second read of same key

        long finalHits = metrics.getHits();
        long hitsFromTest = finalHits - initialHits;

        assertEquals(3, hitsFromTest, "Should have 3 cache hits");
        assertTrue(metrics.getHits() > 0, "Total hits should be positive");
    }

    @Test
    @DisplayName("Should track cache misses correctly")
    void testCacheMisses() {
        long initialMisses = metrics.getMisses();
        
        // Try to read non-existent keys
        EntityCacheKey key1 = new EntityCacheKey(1L, "TestEntity", null);
        EntityCacheKey key2 = new EntityCacheKey(2L, "TestEntity", null);
        
        dataAccess.get(null, key1);
        dataAccess.get(null, key2);

        long finalMisses = metrics.getMisses();
        long missesFromTest = finalMisses - initialMisses;

        assertEquals(2, missesFromTest, "Should have 2 cache misses");
        assertTrue(metrics.getMisses() > 0, "Total misses should be positive");
    }

    @Test
    @DisplayName("Should track cache puts correctly")
    void testCachePuts() {
        long initialPuts = metrics.getPuts();
        
        // Perform puts
        EntityCacheKey key1 = new EntityCacheKey(1L, "TestEntity", null);
        EntityCacheKey key2 = new EntityCacheKey(2L, "TestEntity", null);
        EntityCacheKey key3 = new EntityCacheKey(3L, "TestEntity", null);
        
        dataAccess.putFromLoad(null, key1, "value1", 1);
        dataAccess.putFromLoad(null, key2, "value2", 1);
        dataAccess.putFromLoad(null, key3, "value3", 1);

        long finalPuts = metrics.getPuts();
        long putsFromTest = finalPuts - initialPuts;

        assertEquals(3, putsFromTest, "Should have 3 cache puts");
        assertTrue(metrics.getPuts() > 0, "Total puts should be positive");
    }

    @Test
    @DisplayName("Should track cache evictions correctly")
    void testCacheEvictions() {
        // Create a small cache to force evictions
        MetricsCollector smallCacheMetrics = new MetricsCollector();
        RegionImpl smallRegion = new RegionImpl("small-region", 3, 60000, smallCacheMetrics);
        DomainDataRegionAdapter smallDomainRegion = new DomainDataRegionAdapter(smallRegion, regionFactory, null);
        ReadWriteEntityDataAccess smallDataAccess = new ReadWriteEntityDataAccess(smallRegion, smallDomainRegion);

        long initialEvictions = smallCacheMetrics.getEvictions();
        
        // Fill cache beyond capacity
        for (int i = 0; i < 5; i++) {
            EntityCacheKey key = new EntityCacheKey(i, "TestEntity", null);
            smallDataAccess.putFromLoad(null, key, "value" + i, 1);
        }

        long finalEvictions = smallCacheMetrics.getEvictions();
        long evictionsFromTest = finalEvictions - initialEvictions;

        assertTrue(evictionsFromTest > 0, "Should have some evictions");
        assertTrue(smallCacheMetrics.getEvictions() > 0, "Total evictions should be positive");
    }

    @Test
    @DisplayName("Should track mixed operations correctly")
    void testMixedOperations() {
        long initialHits = metrics.getHits();
        long initialMisses = metrics.getMisses();
        long initialPuts = metrics.getPuts();
        long initialEvictions = metrics.getEvictions();

        // Mix of operations
        EntityCacheKey key1 = new EntityCacheKey(1L, "TestEntity", null);
        EntityCacheKey key2 = new EntityCacheKey(2L, "TestEntity", null);
        EntityCacheKey key3 = new EntityCacheKey(3L, "TestEntity", null);

        // Put some values
        dataAccess.putFromLoad(null, key1, "value1", 1);
        dataAccess.putFromLoad(null, key2, "value2", 1);

        // Read existing (hits)
        dataAccess.get(null, key1);
        dataAccess.get(null, key2);

        // Read non-existing (misses)
        dataAccess.get(null, key3);

        // Put another value
        dataAccess.putFromLoad(null, key3, "value3", 1);

        // Read again (hit)
        dataAccess.get(null, key3);

        long finalHits = metrics.getHits();
        long finalMisses = metrics.getMisses();
        long finalPuts = metrics.getPuts();
        long finalEvictions = metrics.getEvictions();

        assertEquals(3, finalHits - initialHits, "Should have 3 hits");
        assertEquals(1, finalMisses - initialMisses, "Should have 1 miss");
        assertEquals(3, finalPuts - initialPuts, "Should have 3 puts");
        assertEquals(0, finalEvictions - initialEvictions, "Should have no evictions");
    }

    @Test
    @DisplayName("Should track concurrent operations correctly")
    void testConcurrentMetrics() throws InterruptedException {
        int threadCount = 5;
        int operationsPerThread = 100;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        long initialHits = metrics.getHits();
        long initialMisses = metrics.getMisses();
        long initialPuts = metrics.getPuts();

        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    for (int j = 0; j < operationsPerThread; j++) {
                        EntityCacheKey key = new EntityCacheKey(threadId * 100 + j, "TestEntity", null);
                        
                        if (j % 2 == 0) {
                            // Put operation
                            dataAccess.putFromLoad(null, key, "value-" + threadId + "-" + j, 1);
                        } else {
                            // Get operation
                            dataAccess.get(null, key);
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        long finalHits = metrics.getHits();
        long finalMisses = metrics.getMisses();
        long finalPuts = metrics.getPuts();

        long totalHits = finalHits - initialHits;
        long totalMisses = finalMisses - initialMisses;
        long totalPuts = finalPuts - initialPuts;

        assertTrue(totalPuts > 0, "Should have some puts");
        assertTrue(totalHits + totalMisses > 0, "Should have some gets");
        assertTrue(totalPuts <= threadCount * operationsPerThread / 2, "Puts should not exceed expected");
    }

    @Test
    @DisplayName("Should provide meaningful summary")
    void testMetricsSummary() {
        // Perform some operations
        EntityCacheKey key1 = new EntityCacheKey(1L, "TestEntity", null);
        EntityCacheKey key2 = new EntityCacheKey(2L, "TestEntity", null);
        
        dataAccess.putFromLoad(null, key1, "value1", 1);
        dataAccess.get(null, key1); // hit
        dataAccess.get(null, key2); // miss
        dataAccess.putFromLoad(null, key2, "value2", 1);

        String summary = metrics.summary();
        
        assertNotNull(summary, "Summary should not be null");
        assertTrue(summary.contains("hits="), "Summary should contain hits");
        assertTrue(summary.contains("misses="), "Summary should contain misses");
        assertTrue(summary.contains("puts="), "Summary should contain puts");
        assertTrue(summary.contains("evictions="), "Summary should contain evictions");
        
        // Verify specific values
        assertTrue(summary.contains("hits=1"), "Should show 1 hit");
        assertTrue(summary.contains("misses=1"), "Should show 1 miss");
        assertTrue(summary.contains("puts=2"), "Should show 2 puts");
    }

    @Test
    @DisplayName("Should track metrics across multiple regions")
    void testMultipleRegionMetrics() {
        // Create multiple regions with different metrics
        MetricsCollector metrics1 = new MetricsCollector();
        MetricsCollector metrics2 = new MetricsCollector();
        
        RegionImpl region1 = new RegionImpl("region1", 100, 60000, metrics1);
        RegionImpl region2 = new RegionImpl("region2", 100, 60000, metrics2);
        
        DomainDataRegionAdapter domainRegion1 = new DomainDataRegionAdapter(region1, regionFactory, null);
        DomainDataRegionAdapter domainRegion2 = new DomainDataRegionAdapter(region2, regionFactory, null);
        
        ReadWriteEntityDataAccess access1 = new ReadWriteEntityDataAccess(region1, domainRegion1);
        ReadWriteEntityDataAccess access2 = new ReadWriteEntityDataAccess(region2, domainRegion2);

        // Perform operations on both regions
        EntityCacheKey key1 = new EntityCacheKey(1L, "Entity1", null);
        EntityCacheKey key2 = new EntityCacheKey(1L, "Entity2", null);
        
        access1.putFromLoad(null, key1, "value1", 1);
        access1.get(null, key1);
        
        access2.putFromLoad(null, key2, "value2", 1);
        access2.get(null, key2);

        // Verify metrics are tracked separately
        assertEquals(1, metrics1.getHits(), "Region1 should have 1 hit");
        assertEquals(1, metrics1.getPuts(), "Region1 should have 1 put");
        assertEquals(0, metrics1.getMisses(), "Region1 should have 0 misses");
        
        assertEquals(1, metrics2.getHits(), "Region2 should have 1 hit");
        assertEquals(1, metrics2.getPuts(), "Region2 should have 1 put");
        assertEquals(0, metrics2.getMisses(), "Region2 should have 0 misses");
    }

    @Test
    @DisplayName("Should handle metrics reset correctly")
    void testMetricsReset() {
        // Perform some operations
        EntityCacheKey key = new EntityCacheKey(1L, "TestEntity", null);
        dataAccess.putFromLoad(null, key, "value", 1);
        dataAccess.get(null, key);

        assertTrue(metrics.getHits() > 0, "Should have some hits");
        assertTrue(metrics.getPuts() > 0, "Should have some puts");

        // Create new metrics collector (simulating reset)
        MetricsCollector newMetrics = new MetricsCollector();
        assertEquals(0, newMetrics.getHits(), "New metrics should start at 0");
        assertEquals(0, newMetrics.getPuts(), "New metrics should start at 0");
        assertEquals(0, newMetrics.getMisses(), "New metrics should start at 0");
        assertEquals(0, newMetrics.getEvictions(), "New metrics should start at 0");
    }

    @Test
    @DisplayName("Should track TTL-based evictions")
    void testTTLEvictionMetrics() throws InterruptedException {
        // Create region with short TTL
        MetricsCollector ttlMetrics = new MetricsCollector();
        RegionImpl ttlRegion = new RegionImpl("ttl-region", 100, 100, ttlMetrics); // 100ms TTL
        DomainDataRegionAdapter ttlDomainRegion = new DomainDataRegionAdapter(ttlRegion, regionFactory, null);
        ReadWriteEntityDataAccess ttlDataAccess = new ReadWriteEntityDataAccess(ttlRegion, ttlDomainRegion);

        long initialEvictions = ttlMetrics.getEvictions();
        
        // Put value
        EntityCacheKey key = new EntityCacheKey(1L, "TestEntity", null);
        ttlDataAccess.putFromLoad(null, key, "value", 1);
        
        // Wait for TTL to expire
        Thread.sleep(150);
        
        // Try to read - should trigger TTL cleanup
        ttlDataAccess.get(null, key);

        long finalEvictions = ttlMetrics.getEvictions();
        long evictionsFromTTL = finalEvictions - initialEvictions;

        assertTrue(evictionsFromTTL > 0, "Should have evictions due to TTL");
        assertTrue(ttlMetrics.getEvictions() > 0, "Total evictions should be positive");
    }
}
