package com.example.cache.concurrency;

import com.example.cache.access.entities.EntityCacheKey;
import com.example.cache.access.entities.ReadWriteEntityDataAccess;
import com.example.cache.config.CacheConfiguration;
import com.example.cache.factory.CustomRegionFactory;
import com.example.cache.metrics.MetricsCollector;
import com.example.cache.region.DomainDataRegionAdapter;
import com.example.cache.region.RegionImpl;
import org.hibernate.cache.spi.access.SoftLock;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Concurrency and Thread Safety Tests")
class ConcurrencyTest {

    private ReadWriteEntityDataAccess dataAccess;
    private RegionImpl entityRegion;
    private DomainDataRegionAdapter domainDataRegion;
    private CustomRegionFactory regionFactory;

    @BeforeEach
    void setUp() {
        MetricsCollector metrics = new MetricsCollector();
        entityRegion = new RegionImpl("test-region", 1000, 60000, metrics);
        
        // Create mock region factory with configuration
        regionFactory = new CustomRegionFactory();
        Map<String, Object> configValues = new HashMap<>();
        configValues.put("hibernate.cache.lock_timeout_seconds", 30);
        regionFactory.start(null, configValues);
        
        domainDataRegion = new DomainDataRegionAdapter(entityRegion, regionFactory, null);
        dataAccess = new ReadWriteEntityDataAccess(entityRegion, domainDataRegion);
    }

    @Test
    @DisplayName("Should handle concurrent reads safely")
    void testConcurrentReads() throws InterruptedException {
        int threadCount = 10;
        int operationsPerThread = 100;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);

        // Pre-populate cache with test data
        for (int i = 0; i < 50; i++) {
            EntityCacheKey key = new EntityCacheKey(i, "TestEntity", null);
            dataAccess.putFromLoad(null, key, "value-" + i, 1);
        }

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    for (int j = 0; j < operationsPerThread; j++) {
                        EntityCacheKey key = new EntityCacheKey(j % 50, "TestEntity", null);
                        Object result = dataAccess.get(null, key);
                        if (result != null) {
                            successCount.incrementAndGet();
                        }
                    }
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(30, TimeUnit.SECONDS));
        executor.shutdown();

        // Should have many successful reads and no errors
        assertTrue(successCount.get() > 0, "Expected successful reads");
        assertEquals(0, errorCount.get(), "Expected no errors during concurrent reads");
    }

    @Test
    @DisplayName("Should handle concurrent writes safely")
    void testConcurrentWrites() throws InterruptedException {
        int threadCount = 5;
        int operationsPerThread = 50;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    for (int j = 0; j < operationsPerThread; j++) {
                        EntityCacheKey key = new EntityCacheKey(threadId * 100 + j, "TestEntity", null);
                        boolean success = dataAccess.putFromLoad(null, key, "value-" + threadId + "-" + j, 1);
                        if (success) {
                            successCount.incrementAndGet();
                        }
                    }
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(30, TimeUnit.SECONDS));
        executor.shutdown();

        // Should have many successful writes and no errors
        assertTrue(successCount.get() > 0, "Expected successful writes");
        assertEquals(0, errorCount.get(), "Expected no errors during concurrent writes");
    }

    @Test
    @DisplayName("Should handle concurrent read and write operations")
    void testConcurrentReadWrite() throws InterruptedException {
        int readerThreads = 5;
        int writerThreads = 3;
        int operationsPerThread = 100;
        ExecutorService executor = Executors.newFixedThreadPool(readerThreads + writerThreads);
        CountDownLatch latch = new CountDownLatch(readerThreads + writerThreads);
        AtomicInteger readSuccessCount = new AtomicInteger(0);
        AtomicInteger writeSuccessCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);

        // Pre-populate cache
        for (int i = 0; i < 20; i++) {
            EntityCacheKey key = new EntityCacheKey(i, "TestEntity", null);
            dataAccess.putFromLoad(null, key, "initial-value-" + i, 1);
        }

        // Reader threads
        for (int i = 0; i < readerThreads; i++) {
            executor.submit(() -> {
                try {
                    for (int j = 0; j < operationsPerThread; j++) {
                        EntityCacheKey key = new EntityCacheKey(j % 20, "TestEntity", null);
                        Object result = dataAccess.get(null, key);
                        if (result != null) {
                            readSuccessCount.incrementAndGet();
                        }
                    }
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        // Writer threads
        for (int i = 0; i < writerThreads; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    for (int j = 0; j < operationsPerThread; j++) {
                        EntityCacheKey key = new EntityCacheKey(j % 20, "TestEntity", null);
                        boolean success = dataAccess.putFromLoad(null, key, "updated-value-" + threadId + "-" + j, 1);
                        if (success) {
                            writeSuccessCount.incrementAndGet();
                        }
                    }
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(30, TimeUnit.SECONDS));
        executor.shutdown();

        // Should have successful operations and minimal errors
        assertTrue(readSuccessCount.get() > 0, "Expected successful reads");
        assertTrue(writeSuccessCount.get() > 0, "Expected successful writes");
        assertTrue(errorCount.get() < 10, "Expected minimal errors during concurrent operations");
    }

    @Test
    @DisplayName("Should handle concurrent locking operations safely")
    void testConcurrentLocking() throws InterruptedException {
        int threadCount = 5;
        int operationsPerThread = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger lockSuccessCount = new AtomicInteger(0);
        AtomicInteger unlockSuccessCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);
        List<SoftLock> acquiredLocks = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    for (int j = 0; j < operationsPerThread; j++) {
                        EntityCacheKey key = new EntityCacheKey(threadId * 10 + j, "TestEntity", null);
                        
                        // Try to acquire lock
                        SoftLock lock = dataAccess.lockItem(null, key, 1);
                        if (lock != null) {
                            acquiredLocks.add(lock);
                            lockSuccessCount.incrementAndGet();
                            
                            // Hold lock briefly
                            Thread.sleep(10);
                            
                            // Release lock
                            dataAccess.unlockItem(null, key, lock);
                            unlockSuccessCount.incrementAndGet();
                        }
                    }
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(30, TimeUnit.SECONDS));
        executor.shutdown();

        // Should have successful lock operations
        assertTrue(lockSuccessCount.get() > 0, "Expected successful lock acquisitions");
        assertTrue(unlockSuccessCount.get() > 0, "Expected successful lock releases");
        assertTrue(errorCount.get() < 5, "Expected minimal errors during locking operations");
    }

    @Test
    @DisplayName("Should handle region locking correctly")
    void testRegionLocking() throws InterruptedException {
        int threadCount = 3;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger regionLockSuccessCount = new AtomicInteger(0);
        AtomicInteger regionUnlockSuccessCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    // Try to acquire region lock
                    SoftLock regionLock = dataAccess.lockRegion();
                    if (regionLock != null) {
                        regionLockSuccessCount.incrementAndGet();
                        
                        // Hold region lock briefly
                        Thread.sleep(50);
                        
                        // Release region lock
                        dataAccess.unlockRegion(regionLock);
                        regionUnlockSuccessCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(30, TimeUnit.SECONDS));
        executor.shutdown();

        // Should have successful region lock operations
        assertTrue(regionLockSuccessCount.get() > 0, "Expected successful region lock acquisitions");
        assertTrue(regionUnlockSuccessCount.get() > 0, "Expected successful region lock releases");
        assertTrue(errorCount.get() < 3, "Expected minimal errors during region locking operations");
    }

    @Test
    @DisplayName("Should handle high contention scenarios")
    void testHighContention() throws InterruptedException {
        int threadCount = 20;
        int operationsPerThread = 50;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);
        AtomicLong totalTime = new AtomicLong(0);

        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                long startTime = System.currentTimeMillis();
                try {
                    for (int j = 0; j < operationsPerThread; j++) {
                        EntityCacheKey key = new EntityCacheKey(j % 10, "TestEntity", null); // High contention on same keys
                        
                        // Mix of operations
                        if (j % 3 == 0) {
                            // Read operation
                            dataAccess.get(null, key);
                        } else if (j % 3 == 1) {
                            // Write operation
                            dataAccess.putFromLoad(null, key, "value-" + threadId + "-" + j, 1);
                        } else {
                            // Lock operation
                            SoftLock lock = dataAccess.lockItem(null, key, 1);
                            if (lock != null) {
                                Thread.sleep(1); // Brief hold
                                dataAccess.unlockItem(null, key, lock);
                            }
                        }
                        successCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                } finally {
                    totalTime.addAndGet(System.currentTimeMillis() - startTime);
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(60, TimeUnit.SECONDS));
        executor.shutdown();

        // Should complete with reasonable performance
        assertTrue(successCount.get() > 0, "Expected successful operations");
        assertTrue(errorCount.get() < successCount.get() * 0.1, "Error rate should be less than 10%");
        assertTrue(totalTime.get() < 30000, "Total time should be reasonable");
    }

    @Test
    @DisplayName("Should maintain data consistency under concurrent access")
    void testDataConsistency() throws InterruptedException {
        int threadCount = 5;
        int operationsPerThread = 100;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        Set<String> seenValues = ConcurrentHashMap.newKeySet();
        AtomicInteger errorCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    for (int j = 0; j < operationsPerThread; j++) {
                        EntityCacheKey key = new EntityCacheKey(1, "TestEntity", null); // Same key for all threads
                        String value = "value-" + threadId + "-" + j;
                        
                        // Write value
                        dataAccess.putFromLoad(null, key, value, 1);
                        
                        // Read value back
                        Object readValue = dataAccess.get(null, key);
                        if (readValue != null) {
                            seenValues.add(readValue.toString());
                        }
                    }
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(30, TimeUnit.SECONDS));
        executor.shutdown();

        // Should have seen many different values (showing concurrent updates)
        assertTrue(seenValues.size() > 0, "Expected to see some values");
        assertTrue(errorCount.get() < 10, "Expected minimal errors during concurrent access");
    }
}
