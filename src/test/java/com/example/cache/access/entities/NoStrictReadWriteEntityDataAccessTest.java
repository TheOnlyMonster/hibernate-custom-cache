package com.example.cache.access.entities;

import com.example.cache.metrics.MetricsCollector;
import com.example.cache.region.DomainDataRegionAdapter;
import com.example.cache.region.RegionImpl;
import org.hibernate.cache.spi.access.AccessType;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.persister.entity.EntityPersister;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("NoStrictReadWriteEntityDataAccess Tests")
class NoStrictReadWriteEntityDataAccessTest {

    private NoStrictReadWriteEntityDataAccess dataAccess;
    private RegionImpl entityRegion;
    private DomainDataRegionAdapter domainDataRegion;
    private SharedSessionContractImplementor session;
    private SessionFactoryImplementor factory;
    private EntityPersister persister;

    @BeforeEach
    void setUp() {
        MetricsCollector metrics = new MetricsCollector();
        entityRegion = new RegionImpl("test-region", 100, 60000, metrics);
        domainDataRegion = mock(DomainDataRegionAdapter.class);
        dataAccess = new NoStrictReadWriteEntityDataAccess(entityRegion, domainDataRegion);
        
        session = null;
        factory = null;
        persister = null;
        
    }

    @Test
    @DisplayName("Should return correct access type")
    void testAccessType() {
        assertEquals(AccessType.NONSTRICT_READ_WRITE, dataAccess.getAccessType());
    }

    @Test
    @DisplayName("Should throw exception when entityRegion is null")
    void testConstructorNullEntityRegion() {
        assertThrows(IllegalArgumentException.class,
            () -> new NoStrictReadWriteEntityDataAccess(null, domainDataRegion));
    }

    @Test
    @DisplayName("Should throw exception when domainDataRegion is null")
    void testConstructorNullDomainDataRegion() {
        assertThrows(IllegalArgumentException.class,
            () -> new NoStrictReadWriteEntityDataAccess(entityRegion, null));
    }

    @Test
    @DisplayName("Should put and get value from cache")
    void testPutFromLoadAndGet() {
        EntityCacheKey key = new EntityCacheKey(1L, "Entity", null);
        String value = "cached-value";
        
        boolean result = dataAccess.putFromLoad(session, key, value, 1);
        assertTrue(result);
        
        Object retrieved = dataAccess.get(session, key);
        assertEquals(value, retrieved);
        assertTrue(dataAccess.contains(key));
    }

    @Test
    @DisplayName("Should not put null value")
    void testPutFromLoadNull() {
        EntityCacheKey key = new EntityCacheKey(1L, "Entity", null);
        boolean result = dataAccess.putFromLoad(session, key, null, 1);
        assertFalse(result);
        assertNull(dataAccess.get(session, key));
    }

    @Test
    @DisplayName("Should respect minimalPutOverride flag")
    void testPutFromLoadMinimalPutOverride() {
        EntityCacheKey key = new EntityCacheKey(1L, "Entity", null);
        
        // First put
        dataAccess.putFromLoad(session, key, "value1", 1, false);
        
        // Try to put with minimalPutOverride = true
        boolean result = dataAccess.putFromLoad(session, key, "value2", 1, true);
        assertFalse(result);
        
        // Original value should remain
        assertEquals("value1", dataAccess.get(session, key));
    }

    @Test
    @DisplayName("Should allow put without minimalPutOverride")
    void testPutFromLoadWithoutMinimalPutOverride() {
        EntityCacheKey key = new EntityCacheKey(1L, "Entity", null);
        
        dataAccess.putFromLoad(session, key, "value1", 1, false);
        boolean result = dataAccess.putFromLoad(session, key, "value2", 1, false);
        
        assertTrue(result);
        assertEquals("value2", dataAccess.get(session, key));
    }

    @Test
    @DisplayName("Should not support locking - lockItem returns null")
    void testLockItem() {
        EntityCacheKey key = new EntityCacheKey(1L, "Entity", null);
        assertNull(dataAccess.lockItem(session, key, 1));
    }

    @Test
    @DisplayName("Should not support locking - lockRegion returns null")
    void testLockRegion() {
        assertNull(dataAccess.lockRegion());
    }

    @Test
    @DisplayName("Should handle unlockItem gracefully")
    void testUnlockItem() {
        EntityCacheKey key = new EntityCacheKey(1L, "Entity", null);
        // Should not throw
        dataAccess.unlockItem(session, key, null);
    }

    @Test
    @DisplayName("Should handle unlockRegion gracefully")
    void testUnlockRegion() {
        // Should not throw
        dataAccess.unlockRegion(null);
    }

    @Test
    @DisplayName("Should evict entity")
    void testEvict() {
        EntityCacheKey key = new EntityCacheKey(1L, "Entity", null);
        dataAccess.putFromLoad(session, key, "value", 1);
        
        dataAccess.evict(key);
        
        assertNull(dataAccess.get(session, key));
        assertFalse(dataAccess.contains(key));
    }

    @Test
    @DisplayName("Should evict all entities")
    void testEvictAll() {
        EntityCacheKey key1 = new EntityCacheKey(1L, "Entity", null);
        EntityCacheKey key2 = new EntityCacheKey(2L, "Entity", null);
        
        dataAccess.putFromLoad(session, key1, "value1", 1);
        dataAccess.putFromLoad(session, key2, "value2", 1);
        
        dataAccess.evictAll();
        
        assertNull(dataAccess.get(session, key1));
        assertNull(dataAccess.get(session, key2));
    }

    @Test
    @DisplayName("Should remove entity")
    void testRemove() {
        EntityCacheKey key = new EntityCacheKey(1L, "Entity", null);
        dataAccess.putFromLoad(session, key, "value", 1);
        
        dataAccess.remove(session, key);
        
        assertNull(dataAccess.get(session, key));
    }

    @Test
    @DisplayName("Should remove all entities")
    void testRemoveAll() {
        EntityCacheKey key1 = new EntityCacheKey(1L, "Entity", null);
        EntityCacheKey key2 = new EntityCacheKey(2L, "Entity", null);
        
        dataAccess.putFromLoad(session, key1, "value1", 1);
        dataAccess.putFromLoad(session, key2, "value2", 1);
        
        dataAccess.removeAll(session);
        
        assertNull(dataAccess.get(session, key1));
        assertNull(dataAccess.get(session, key2));
    }

    @Test
    @DisplayName("Should return false for insert")
    void testInsert() {
        EntityCacheKey key = new EntityCacheKey(1L, "Entity", null);
        boolean result = dataAccess.insert(session, key, "value", 1);
        assertFalse(result);
    }

    @Test
    @DisplayName("Should cache value after insert")
    void testAfterInsert() {
        EntityCacheKey key = new EntityCacheKey(1L, "Entity", null);
        
        boolean result = dataAccess.afterInsert(session, key, "value", 1);
        assertTrue(result);
        
        assertEquals("value", dataAccess.get(session, key));
    }

    @Test
    @DisplayName("Should not cache null value after insert")
    void testAfterInsertNull() {
        EntityCacheKey key = new EntityCacheKey(1L, "Entity", null);
        
        boolean result = dataAccess.afterInsert(session, key, null, 1);
        assertFalse(result);
        
        assertNull(dataAccess.get(session, key));
    }

    @Test
    @DisplayName("Should evict on update and return false")
    void testUpdate() {
        EntityCacheKey key = new EntityCacheKey(1L, "Entity", null);
        dataAccess.putFromLoad(session, key, "value1", 1);
        
        boolean result = dataAccess.update(session, key, "value2", 2, 1);
        assertFalse(result);
        
        // Should be evicted, not updated
        assertNull(dataAccess.get(session, key));
    }

    @Test
    @DisplayName("Should handle update on non-existent key")
    void testUpdateNonExistent() {
        EntityCacheKey key = new EntityCacheKey(1L, "Entity", null);
        boolean result = dataAccess.update(session, key, "value", 1, 0);
        assertFalse(result);
    }

    @Test
    @DisplayName("Should cache new value after update")
    void testAfterUpdate() {
        EntityCacheKey key = new EntityCacheKey(1L, "Entity", null);
        dataAccess.putFromLoad(session, key, "value1", 1);
        
        boolean result = dataAccess.afterUpdate(session, key, "value2", 2, 1, null);
        assertTrue(result);
        
        // Should have new value in cache
        assertEquals("value2", dataAccess.get(session, key));
    }

    @Test
    @DisplayName("Should not cache null value after update")
    void testAfterUpdateNull() {
        EntityCacheKey key = new EntityCacheKey(1L, "Entity", null);
        dataAccess.putFromLoad(session, key, "value1", 1);
        
        boolean result = dataAccess.afterUpdate(session, key, null, 2, 1, null);
        assertFalse(result);
    }

    @Test
    @DisplayName("Should generate cache key correctly")
    void testGenerateCacheKey() {
        Object key = dataAccess.generateCacheKey(1L, persister, factory, "tenant1");
        
        assertNotNull(key);
        assertTrue(key instanceof EntityCacheKey);
        EntityCacheKey cacheKey = (EntityCacheKey) key;
        assertEquals(1L, cacheKey.getId());
        assertEquals("com.example.Entity", cacheKey.getEntityName());
        assertEquals("tenant1", cacheKey.getTenantId());
    }

    @Test
    @DisplayName("Should throw exception for null id in generateCacheKey")
    void testGenerateCacheKeyNullId() {
        assertThrows(IllegalArgumentException.class,
            () -> dataAccess.generateCacheKey(null, persister, factory, "tenant1"));
    }


    @Test
    @DisplayName("Should extract cache key id correctly")
    void testGetCacheKeyId() {
        EntityCacheKey cacheKey = new EntityCacheKey(1L, "Entity", "tenant1");
        Object id = dataAccess.getCacheKeyId(cacheKey);
        assertEquals(1L, id);
    }

    @Test
    @DisplayName("Should throw exception for null cache key in getCacheKeyId")
    void testGetCacheKeyIdNull() {
        assertThrows(IllegalArgumentException.class,
            () -> dataAccess.getCacheKeyId(null));
    }

    @Test
    @DisplayName("Should throw exception for wrong cache key type in getCacheKeyId")
    void testGetCacheKeyIdWrongType() {
        assertThrows(IllegalArgumentException.class,
            () -> dataAccess.getCacheKeyId("not-a-cache-key"));
    }

    @Test
    @DisplayName("Should handle multi-tenant entities")
    void testMultiTenant() {
        EntityCacheKey key1 = new EntityCacheKey(1L, "Entity", "tenant1");
        EntityCacheKey key2 = new EntityCacheKey(1L, "Entity", "tenant2");
        
        dataAccess.putFromLoad(session, key1, "tenant1-value", 1);
        dataAccess.putFromLoad(session, key2, "tenant2-value", 1);
        
        assertEquals("tenant1-value", dataAccess.get(session, key1));
        assertEquals("tenant2-value", dataAccess.get(session, key2));
    }

    @Test
    @DisplayName("Should return correct region")
    void testGetRegion() {
        assertEquals(domainDataRegion, dataAccess.getRegion());
    }

    @Test
    @DisplayName("Should handle concurrent reads and writes")
    void testConcurrentAccess() throws InterruptedException {
        int threadCount = 10;
        int operationsPerThread = 50;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    for (int j = 0; j < operationsPerThread; j++) {
                        EntityCacheKey key = new EntityCacheKey((long)(threadId % 5), "Entity", null);
                        
                        try {
                            // Random operations
                            if (j % 4 == 0) {
                                dataAccess.putFromLoad(session, key, "value-" + threadId, 1);
                            } else if (j % 4 == 1) {
                                dataAccess.get(session, key);
                            } else if (j % 4 == 2) {
                                dataAccess.afterInsert(session, key, "inserted-" + threadId, 1);
                            } else {
                                dataAccess.afterUpdate(session, key, "updated-" + threadId, 2, 1, null);
                            }
                            successCount.incrementAndGet();
                        } catch (Exception e) {
                            // Should not happen in NONSTRICT_READ_WRITE
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(30, TimeUnit.SECONDS));
        executor.shutdown();
        
        // Should complete without deadlock
        assertTrue(successCount.get() > 0);
    }

    @Test
    @DisplayName("Should handle evict on non-existent key gracefully")
    void testEvictNonExistent() {
        EntityCacheKey key = new EntityCacheKey(1L, "Entity", null);
        // Should not throw
        dataAccess.evict(key);
    }

    @Test
    @DisplayName("Should handle remove on non-existent key gracefully")
    void testRemoveNonExistent() {
        EntityCacheKey key = new EntityCacheKey(1L, "Entity", null);
        // Should not throw
        dataAccess.remove(session, key);
    }

    @Test
    @DisplayName("Should handle different entity types")
    void testDifferentEntityTypes() {
        EntityCacheKey key1 = new EntityCacheKey(1L, "Entity1", null);
        EntityCacheKey key2 = new EntityCacheKey(1L, "Entity2", null);
        
        dataAccess.putFromLoad(session, key1, "entity1-value", 1);
        dataAccess.putFromLoad(session, key2, "entity2-value", 1);
        
        assertEquals("entity1-value", dataAccess.get(session, key1));
        assertEquals("entity2-value", dataAccess.get(session, key2));
    }

    @Test
    @DisplayName("Should return false for contains on non-existent key")
    void testContainsNonExistent() {
        EntityCacheKey key = new EntityCacheKey(1L, "Entity", null);
        assertFalse(dataAccess.contains(key));
    }

    @Test
    @DisplayName("Should return null for get on non-existent key")
    void testGetNonExistent() {
        EntityCacheKey key = new EntityCacheKey(1L, "Entity", null);
        assertNull(dataAccess.get(session, key));
    }

    @Test
    @DisplayName("Should allow stale reads - characteristic of NONSTRICT_READ_WRITE")
    void testStaleReads() {
        EntityCacheKey key = new EntityCacheKey(1L, "Entity", null);
        
        // Put initial value
        dataAccess.putFromLoad(session, key, "value1", 1);
        assertEquals("value1", dataAccess.get(session, key));
        
        // Update evicts but doesn't lock
        dataAccess.update(session, key, "value2", 2, 1);
        
        // Cache is evicted, so we get null (not stale data)
        assertNull(dataAccess.get(session, key));
        
        // After update completes, new value is cached
        dataAccess.afterUpdate(session, key, "value2", 2, 1, null);
        assertEquals("value2", dataAccess.get(session, key));
    }

    @Test
    @DisplayName("Should handle rapid insert and update cycles")
    void testRapidInsertUpdateCycles() {
        EntityCacheKey key = new EntityCacheKey(1L, "Entity", null);
        
        // Insert
        dataAccess.afterInsert(session, key, "inserted", 1);
        assertEquals("inserted", dataAccess.get(session, key));
        
        // Update cycle 1
        dataAccess.update(session, key, "updating1", 2, 1);
        dataAccess.afterUpdate(session, key, "updated1", 2, 1, null);
        assertEquals("updated1", dataAccess.get(session, key));
        
        // Update cycle 2
        dataAccess.update(session, key, "updating2", 3, 2);
        dataAccess.afterUpdate(session, key, "updated2", 3, 2, null);
        assertEquals("updated2", dataAccess.get(session, key));
    }

    @Test
    @DisplayName("Should handle afterUpdate on non-existent key")
    void testAfterUpdateNonExistent() {
        EntityCacheKey key = new EntityCacheKey(1L, "Entity", null);
        boolean result = dataAccess.afterUpdate(session, key, "value", 1, 0, null);
        assertTrue(result);
        
        // Value should be cached even if it didn't exist before
        assertEquals("value", dataAccess.get(session, key));
    }

    @Test
    @DisplayName("Should handle multiple sequential operations")
    void testSequentialOperations() {
        EntityCacheKey key = new EntityCacheKey(1L, "Entity", null);
        
        // Initial load
        dataAccess.putFromLoad(session, key, "loaded", 1);
        assertTrue(dataAccess.contains(key));
        
        // Remove
        dataAccess.remove(session, key);
        assertFalse(dataAccess.contains(key));
        
        // Insert
        dataAccess.afterInsert(session, key, "inserted", 1);
        assertTrue(dataAccess.contains(key));
        
        // Update
        dataAccess.update(session, key, "updating", 2, 1);
        assertFalse(dataAccess.contains(key)); // Evicted during update
        
        dataAccess.afterUpdate(session, key, "updated", 2, 1, null);
        assertTrue(dataAccess.contains(key));
        
        // Evict
        dataAccess.evict(key);
        assertFalse(dataAccess.contains(key));
    }

    @Test
    @DisplayName("Should handle empty cache operations")
    void testEmptyCacheOperations() {
        // Should not throw on empty cache
        dataAccess.evictAll();
        dataAccess.removeAll(session);
        
        EntityCacheKey key = new EntityCacheKey(1L, "Entity", null);
        assertNull(dataAccess.get(session, key));
        assertFalse(dataAccess.contains(key));
    }
}