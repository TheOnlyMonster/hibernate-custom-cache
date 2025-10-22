package com.example.cache.performance;

import com.example.cache.access.entities.EntityCacheKey;
import com.example.cache.access.entities.ReadWriteEntityDataAccess;
import com.example.cache.factory.CustomRegionFactory;
import com.example.cache.metrics.MetricsCollector;
import com.example.cache.region.DomainDataRegionAdapter;
import com.example.cache.region.RegionImpl;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Disabled;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Performance and Stress Tests")
class PerformanceTest {

    private ReadWriteEntityDataAccess dataAccess;
    private RegionImpl entityRegion;
    private DomainDataRegionAdapter domainDataRegion;
    private CustomRegionFactory regionFactory;

    @BeforeEach
    void setUp() {
        MetricsCollector metrics = new MetricsCollector();
        entityRegion = new RegionImpl("test-region", 10000, 60000, metrics);
        
        regionFactory = new CustomRegionFactory();
        Map<String, Object> configValues = new HashMap<>();
        configValues.put("hibernate.cache.lock_timeout_seconds", 1); // 1 second timeout for tests
        regionFactory.start(null, configValues);
        
        domainDataRegion = new DomainDataRegionAdapter(entityRegion, regionFactory, null);
        dataAccess = new ReadWriteEntityDataAccess(entityRegion, domainDataRegion);
    }

    @Test
    @DisplayName("Should handle high throughput read operations")
    void testHighThroughputReads() throws InterruptedException {
        int threadCount = 10;
        int operationsPerThread = 1000;
        
        // Pre-populate cache
        for (int i = 0; i < 100; i++) {
            EntityCacheKey key = new EntityCacheKey(i, "TestEntity", null);
            dataAccess.putFromLoad(null, key, "value-" + i, 1);
        }

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicLong totalOperations = new AtomicLong(0);
        AtomicLong totalTime = new AtomicLong(0);

        long startTime = System.currentTimeMillis();

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                long threadStartTime = System.currentTimeMillis();
                try {
                    for (int j = 0; j < operationsPerThread; j++) {
                        EntityCacheKey key = new EntityCacheKey(j % 100, "TestEntity", null);
                        dataAccess.get(null, key);
                        totalOperations.incrementAndGet();
                    }
                } finally {
                    totalTime.addAndGet(System.currentTimeMillis() - threadStartTime);
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(30, TimeUnit.SECONDS));
        executor.shutdown();

        long totalDuration = System.currentTimeMillis() - startTime;
        long totalOps = totalOperations.get();
        double opsPerSecond = (totalOps * 1000.0) / totalDuration;

        System.out.printf("High throughput reads: %d operations in %d ms (%.2f ops/sec)%n", 
                         totalOps, totalDuration, opsPerSecond);

        assertTrue(opsPerSecond > 1000, "Should achieve at least 1000 ops/sec");
        assertTrue(totalOps == threadCount * operationsPerThread, "All operations should complete");
    }

    @Test
    @DisplayName("Should handle high throughput write operations")
    void testHighThroughputWrites() throws InterruptedException {
        int threadCount = 5;
        int operationsPerThread = 500;

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicLong totalOperations = new AtomicLong(0);

        long startTime = System.currentTimeMillis();

        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    for (int j = 0; j < operationsPerThread; j++) {
                        EntityCacheKey key = new EntityCacheKey(threadId * 1000 + j, "TestEntity", null);
                        dataAccess.putFromLoad(null, key, "value-" + threadId + "-" + j, 1);
                        totalOperations.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(30, TimeUnit.SECONDS));
        executor.shutdown();

        long totalDuration = System.currentTimeMillis() - startTime;
        long totalOps = totalOperations.get();
        double opsPerSecond = (totalOps * 1000.0) / totalDuration;

        System.out.printf("High throughput writes: %d operations in %d ms (%.2f ops/sec)%n", 
                         totalOps, totalDuration, opsPerSecond);

        assertTrue(opsPerSecond > 500, "Should achieve at least 500 ops/sec");
        assertTrue(totalOps == threadCount * operationsPerThread, "All operations should complete");
    }

    @Test
    @DisplayName("Should handle mixed read/write workload efficiently")
    void testMixedWorkload() throws InterruptedException {
        int threadCount = 8;
        int operationsPerThread = 500;

        // Pre-populate cache
        for (int i = 0; i < 50; i++) {
            EntityCacheKey key = new EntityCacheKey(i, "TestEntity", null);
            dataAccess.putFromLoad(null, key, "initial-value-" + i, 1);
        }

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicLong readOps = new AtomicLong(0);
        AtomicLong writeOps = new AtomicLong(0);

        long startTime = System.currentTimeMillis();

        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    for (int j = 0; j < operationsPerThread; j++) {
                        EntityCacheKey key = new EntityCacheKey(j % 50, "TestEntity", null);
                        
                        if (j % 2 == 0) {
                            // Read operation
                            dataAccess.get(null, key);
                            readOps.incrementAndGet();
                        } else {
                            // Write operation
                            dataAccess.putFromLoad(null, key, "updated-value-" + threadId + "-" + j, 1);
                            writeOps.incrementAndGet();
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(30, TimeUnit.SECONDS));
        executor.shutdown();

        long totalDuration = System.currentTimeMillis() - startTime;
        long totalOps = readOps.get() + writeOps.get();
        double opsPerSecond = (totalOps * 1000.0) / totalDuration;

        System.out.printf("Mixed workload: %d reads, %d writes in %d ms (%.2f ops/sec)%n", 
                         readOps.get(), writeOps.get(), totalDuration, opsPerSecond);

        assertTrue(opsPerSecond > 800, "Should achieve at least 800 ops/sec");
        assertTrue(totalOps == threadCount * operationsPerThread, "All operations should complete");
    }

    @Test
    @DisplayName("Should handle memory pressure gracefully")
    void testMemoryPressure() throws InterruptedException {
        int maxEntries = 1000;
        int operationsToExceedCapacity = maxEntries + 500;

        // Create a new region with limited capacity
        MetricsCollector metrics = new MetricsCollector();
        RegionImpl limitedRegion = new RegionImpl("limited-region", maxEntries, 60000, metrics);
        DomainDataRegionAdapter limitedDomainRegion = new DomainDataRegionAdapter(limitedRegion, regionFactory, null);
        ReadWriteEntityDataAccess limitedDataAccess = new ReadWriteEntityDataAccess(limitedRegion, limitedDomainRegion);

        long startTime = System.currentTimeMillis();

        // Fill cache beyond capacity
        for (int i = 0; i < operationsToExceedCapacity; i++) {
            EntityCacheKey key = new EntityCacheKey(i, "TestEntity", null);
            limitedDataAccess.putFromLoad(null, key, "value-" + i, 1);
        }

        long duration = System.currentTimeMillis() - startTime;
        int actualSize = limitedRegion.size();

        System.out.printf("Memory pressure test: %d operations in %d ms, final size: %d%n", 
                         operationsToExceedCapacity, duration, actualSize);

        // Should not exceed capacity
        assertTrue(actualSize <= maxEntries, "Cache size should not exceed capacity");
        assertTrue(actualSize > 0, "Cache should not be empty");
        assertTrue(duration < 10000, "Operations should complete in reasonable time");
    }

    @Test
    @DisplayName("Should handle TTL expiration efficiently")
    void testTTLExpiration() throws InterruptedException {
        int maxEntries = 1000;
        long ttlMs = 100; // Very short TTL for testing

        // Create region with short TTL
        MetricsCollector metrics = new MetricsCollector();
        RegionImpl ttlRegion = new RegionImpl("ttl-region", maxEntries, ttlMs, metrics);
        DomainDataRegionAdapter ttlDomainRegion = new DomainDataRegionAdapter(ttlRegion, regionFactory, null);
        ReadWriteEntityDataAccess ttlDataAccess = new ReadWriteEntityDataAccess(ttlRegion, ttlDomainRegion);

        // Fill cache
        for (int i = 0; i < 100; i++) {
            EntityCacheKey key = new EntityCacheKey(i, "TestEntity", null);
            ttlDataAccess.putFromLoad(null, key, "value-" + i, 1);
        }

        int initialSize = ttlRegion.size();
        assertEquals(100, initialSize, "Cache should be full initially");

        // Wait for TTL to expire
        Thread.sleep(ttlMs + 50);

        // Try to read - should trigger TTL cleanup
        for (int i = 0; i < 100; i++) {
            EntityCacheKey key = new EntityCacheKey(i, "TestEntity", null);
            ttlDataAccess.get(null, key);
        }

        int finalSize = ttlRegion.size();
        long evictions = metrics.getEvictions();

        System.out.printf("TTL expiration test: initial size: %d, final size: %d, evictions: %d%n", 
                         initialSize, finalSize, evictions);

        assertTrue(finalSize < initialSize, "Cache size should decrease due to TTL expiration");
        assertTrue(evictions > 0, "Should have evictions due to TTL");
    }

    @Test
    @DisplayName("Should maintain performance under sustained load")
    void testSustainedLoad() throws InterruptedException {
        int threadCount = 4;
        int operationsPerThread = 2000;
        int durationSeconds = 10;

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicLong totalOperations = new AtomicLong(0);
        AtomicLong errorCount = new AtomicLong(0);
        List<Long> operationTimes = Collections.synchronizedList(new ArrayList<>());

        long startTime = System.currentTimeMillis();
        long endTime = startTime + (durationSeconds * 1000);

        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    while (System.currentTimeMillis() < endTime) {
                        long opStartTime = System.currentTimeMillis();
                        
                        EntityCacheKey key = new EntityCacheKey(threadId * 100 + (int)(Math.random() * 50), "TestEntity", null);
                        
                        if (Math.random() < 0.7) {
                            // 70% reads
                            dataAccess.get(null, key);
                        } else {
                            // 30% writes
                            dataAccess.putFromLoad(null, key, "value-" + System.currentTimeMillis(), 1);
                        }
                        
                        long opDuration = System.currentTimeMillis() - opStartTime;
                        operationTimes.add(opDuration);
                        totalOperations.incrementAndGet();
                    }
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(durationSeconds + 5, TimeUnit.SECONDS));
        executor.shutdown();

        long totalDuration = System.currentTimeMillis() - startTime;
        long totalOps = totalOperations.get();
        double opsPerSecond = (totalOps * 1000.0) / totalDuration;
        double errorRate = (errorCount.get() * 100.0) / totalOps;

        // Calculate percentiles
        Collections.sort(operationTimes);
        long p50 = operationTimes.get((int)(operationTimes.size() * 0.5));
        long p95 = operationTimes.get((int)(operationTimes.size() * 0.95));
        long p99 = operationTimes.get((int)(operationTimes.size() * 0.99));

        System.out.printf("Sustained load: %d operations in %d ms (%.2f ops/sec), error rate: %.2f%%%n", 
                         totalOps, totalDuration, opsPerSecond, errorRate);
        System.out.printf("Latency percentiles: P50=%dms, P95=%dms, P99=%dms%n", p50, p95, p99);

        assertTrue(opsPerSecond > 500, "Should maintain at least 500 ops/sec");
        assertTrue(errorRate < 1.0, "Error rate should be less than 1%");
        assertTrue(p95 < 100, "95th percentile latency should be less than 100ms");
    }

    @Test
    @DisplayName("Should handle cache warming efficiently")
    void testCacheWarming() throws InterruptedException {
        int warmupEntries = 5000;
        int threadCount = 4;

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicLong totalOperations = new AtomicLong(0);

        long startTime = System.currentTimeMillis();

        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    int entriesPerThread = warmupEntries / threadCount;
                    for (int j = 0; j < entriesPerThread; j++) {
                        EntityCacheKey key = new EntityCacheKey(threadId * entriesPerThread + j, "TestEntity", null);
                        dataAccess.putFromLoad(null, key, "warmup-value-" + j, 1);
                        totalOperations.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(30, TimeUnit.SECONDS));
        executor.shutdown();

        long totalDuration = System.currentTimeMillis() - startTime;
        long totalOps = totalOperations.get();
        double opsPerSecond = (totalOps * 1000.0) / totalDuration;

        System.out.printf("Cache warming: %d operations in %d ms (%.2f ops/sec)%n", 
                         totalOps, totalDuration, opsPerSecond);

        assertTrue(opsPerSecond > 1000, "Should achieve at least 1000 ops/sec during warming");
        assertTrue(totalOps == warmupEntries, "All warmup operations should complete");
    }

    @Test
    @DisplayName("Should handle lock contention efficiently")
    void testLockContention() throws InterruptedException {
        int threadCount = 10;
        int operationsPerThread = 100;
        int sharedKeyCount = 5; // Few shared keys to create contention

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicLong successfulLocks = new AtomicLong(0);
        AtomicLong failedLocks = new AtomicLong(0);

        long startTime = System.currentTimeMillis();

        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    for (int j = 0; j < operationsPerThread; j++) {
                        EntityCacheKey key = new EntityCacheKey(j % sharedKeyCount, "TestEntity", null);
                        
                        try {
                            var lock = dataAccess.lockItem(null, key, 1);
                            if (lock != null) {
                                successfulLocks.incrementAndGet();
                                Thread.sleep(10); // Hold lock for 10ms to simulate real work
                                dataAccess.unlockItem(null, key, lock);
                            } else {
                                failedLocks.incrementAndGet();
                            }
                        } catch (Exception e) {
                            failedLocks.incrementAndGet();
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(30, TimeUnit.SECONDS));
        executor.shutdown();

        long totalDuration = System.currentTimeMillis() - startTime;
        long totalLocks = successfulLocks.get() + failedLocks.get();
        double successRate = (successfulLocks.get() * 100.0) / totalLocks;

        System.out.printf("Lock contention: %d successful, %d failed locks in %d ms (%.2f%% success rate)%n", 
                         successfulLocks.get(), failedLocks.get(), totalDuration, successRate);

        assertTrue(successRate > 50, "Success rate should be at least 50%");
        assertTrue(totalLocks > 0, "Should have attempted some locks");
    }
}
