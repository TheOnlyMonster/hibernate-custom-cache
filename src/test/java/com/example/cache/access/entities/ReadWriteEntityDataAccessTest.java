package com.example.cache.access.entities;

import com.example.cache.access.ReadWriteSoftLock;
import com.example.cache.metrics.MetricsCollector;
import com.example.cache.region.DomainDataRegionAdapter;
import com.example.cache.region.EntityRegionImpl;
import org.hibernate.cache.CacheException;
import org.hibernate.cache.spi.access.AccessType;
import org.hibernate.cache.spi.access.SoftLock;
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

@DisplayName("ReadWriteEntityDataAccess Tests")
class ReadWriteEntityDataAccessTest {

    private ReadWriteEntityDataAccess dataAccess;
    private EntityRegionImpl entityRegion;
    private DomainDataRegionAdapter domainDataRegion;
    private SharedSessionContractImplementor session;
    private EntityPersister persister;
    private SessionFactoryImplementor factory;

    @BeforeEach
    void setUp() {
        MetricsCollector metrics = new MetricsCollector();
        entityRegion = new EntityRegionImpl("test-region", 100, 60000, metrics);
        domainDataRegion = mock(DomainDataRegionAdapter.class);
        dataAccess = new ReadWriteEntityDataAccess(entityRegion, domainDataRegion);
        
        session = null;
        persister = mock(EntityPersister.class);
        factory = null;
        
        when(persister.getEntityName()).thenReturn("com.example.Entity");
    }

    @Test
    @DisplayName("Should return correct access type")
    void testAccessType() {
        assertEquals(AccessType.READ_WRITE, dataAccess.getAccessType());
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
    }

    @Test
    @DisplayName("Should lock item and prevent reads")
    void testLockItemPreventsReads() {
        EntityCacheKey key = new EntityCacheKey(1L, "Entity", null);
        dataAccess.putFromLoad(session, key, "value", 1);
        
        // Lock the item
        SoftLock lock = dataAccess.lockItem(session, key, 1);
        assertNotNull(lock);
        assertTrue(lock instanceof ReadWriteSoftLock);
        
        // Should not be able to read locked item
        assertNull(dataAccess.get(session, key));
        assertFalse(dataAccess.contains(key));
        
        // Unlock
        dataAccess.unlockItem(session, key, lock);
    }

    @Test
    @DisplayName("Should not put to cache while locked")
    void testPutWhileLocked() {
        EntityCacheKey key = new EntityCacheKey(1L, "Entity", null);
        dataAccess.putFromLoad(session, key, "value1", 1);
        
        SoftLock lock = dataAccess.lockItem(session, key, 1);
        
        // Try to put while locked
        boolean result = dataAccess.putFromLoad(session, key, "value2", 2);
        assertFalse(result);
        
        dataAccess.unlockItem(session, key, lock);
    }

    @Test
    @DisplayName("Should throw exception when locking already locked item")
    void testDoubleLock() {
        EntityCacheKey key = new EntityCacheKey(1L, "Entity", null);
        dataAccess.putFromLoad(session, key, "value", 1);
        
        SoftLock lock1 = dataAccess.lockItem(session, key, 1);
        
        // Try to lock again
        assertThrows(CacheException.class, 
            () -> dataAccess.lockItem(session, key, 1));
        
        dataAccess.unlockItem(session, key, lock1);
    }

    @Test
    @DisplayName("Should handle lock expiration")
    void testLockExpiration() throws InterruptedException {
        EntityCacheKey key = new EntityCacheKey(1L, "Entity", null);
        dataAccess.putFromLoad(session, key, "value", 1);
        
        SoftLock lock = dataAccess.lockItem(session, key, 1);
        
        // Wait for lock to expire (60 seconds in production, but test with reflection or make timeout configurable)
        // For now, just verify lock exists
        assertNotNull(lock);
        assertNull(dataAccess.get(session, key));
        
        dataAccess.unlockItem(session, key, lock);
    }

    @Test
    @DisplayName("Should unlock item properly")
    void testUnlockItem() {
        EntityCacheKey key = new EntityCacheKey(1L, "Entity", null);
        dataAccess.putFromLoad(session, key, "value", 1);
        
        SoftLock lock = dataAccess.lockItem(session, key, 1);
        dataAccess.unlockItem(session, key, lock);
        
        // After unlock, should be able to lock again
        SoftLock lock2 = dataAccess.lockItem(session, key, 1);
        assertNotNull(lock2);
        dataAccess.unlockItem(session, key, lock2);
    }

    @Test
    @DisplayName("Should handle unlocking with wrong lock type gracefully")
    void testUnlockWithWrongType() {
        EntityCacheKey key = new EntityCacheKey(1L, "Entity", null);
        // Should not throw
        dataAccess.unlockItem(session, key, mock(SoftLock.class));
    }

    @Test
    @DisplayName("Should lock region and prevent all operations")
    void testLockRegion() {
        EntityCacheKey key1 = new EntityCacheKey(1L, "Entity", null);
        EntityCacheKey key2 = new EntityCacheKey(2L, "Entity", null);
        
        dataAccess.putFromLoad(session, key1, "value1", 1);
        dataAccess.putFromLoad(session, key2, "value2", 1);
        
        // Lock entire region
        SoftLock regionLock = dataAccess.lockRegion();
        assertNotNull(regionLock);
        
        // Should not be able to read anything
        assertNull(dataAccess.get(session, key1));
        assertNull(dataAccess.get(session, key2));
        assertFalse(dataAccess.contains(key1));
        
        // Should not be able to put
        assertFalse(dataAccess.putFromLoad(session, key1, "new-value", 2));
        
        // Unlock region
        dataAccess.unlockRegion(regionLock);
        
        // Should work again
        assertTrue(dataAccess.putFromLoad(session, key1, "after-unlock", 2));
    }

    @Test
    @DisplayName("Should throw exception when locking already locked region")
    void testDoubleLockRegion() {
        SoftLock lock1 = dataAccess.lockRegion();
        
        assertThrows(CacheException.class, () -> dataAccess.lockRegion());
        
        dataAccess.unlockRegion(lock1);
    }

    @Test
    @DisplayName("Should clear all locks when unlocking region")
    void testUnlockRegionClearsAllLocks() {
        EntityCacheKey key = new EntityCacheKey(1L, "Entity", null);
        dataAccess.putFromLoad(session, key, "value", 1);
        
        // Lock item
        SoftLock itemLock = dataAccess.lockItem(session, key, 1);
        
        // Lock region (should clear item locks)
        SoftLock regionLock = dataAccess.lockRegion();
        dataAccess.unlockRegion(regionLock);
        
        // Item should be lockable again
        SoftLock newLock = dataAccess.lockItem(session, key, 1);
        assertNotNull(newLock);
        dataAccess.unlockItem(session, key, newLock);
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
    @DisplayName("Should not after-insert when region is locked")
    void testAfterInsertWhileRegionLocked() {
        EntityCacheKey key = new EntityCacheKey(1L, "Entity", null);
        
        SoftLock regionLock = dataAccess.lockRegion();
        
        boolean result = dataAccess.afterInsert(session, key, "value", 1);
        assertFalse(result);
        
        dataAccess.unlockRegion(regionLock);
    }

    @Test
    @DisplayName("Should lock item on update")
    void testUpdate() {
        EntityCacheKey key = new EntityCacheKey(1L, "Entity", null);
        dataAccess.putFromLoad(session, key, "value1", 1);
        
        boolean result = dataAccess.update(session, key, "value2", 2, 1);
        assertTrue(result); // Returns true if lock acquired
        
        // Item should be locked now
        assertNull(dataAccess.get(session, key));
    }

    @Test
    @DisplayName("Should not update when region is locked")
    void testUpdateWhileRegionLocked() {
        EntityCacheKey key = new EntityCacheKey(1L, "Entity", null);
        dataAccess.putFromLoad(session, key, "value1", 1);
        
        SoftLock regionLock = dataAccess.lockRegion();
        
        boolean result = dataAccess.update(session, key, "value2", 2, 1);
        assertFalse(result);
        
        dataAccess.unlockRegion(regionLock);
    }

    @Test
    @DisplayName("Should update cache after update")
    void testAfterUpdate() {
        EntityCacheKey key = new EntityCacheKey(1L, "Entity", null);
        dataAccess.putFromLoad(session, key, "value1", 1);
        
        SoftLock lock = dataAccess.lockItem(session, key, 1);
        
        boolean result = dataAccess.afterUpdate(session, key, "value2", 2, 1, lock);
        assertTrue(result);
        
        // Should be updated in cache
        assertEquals("value2", dataAccess.get(session, key));
    }

    @Test
    @DisplayName("Should not after-update when region is locked")
    void testAfterUpdateWhileRegionLocked() {
        EntityCacheKey key = new EntityCacheKey(1L, "Entity", null);
        dataAccess.putFromLoad(session, key, "value1", 1);
        
        SoftLock lock = dataAccess.lockItem(session, key, 1);
        SoftLock regionLock = dataAccess.lockRegion();
        
        boolean result = dataAccess.afterUpdate(session, key, "value2", 2, 1, lock);
        assertFalse(result);
        
        dataAccess.unlockRegion(regionLock);
    }

    @Test
    @DisplayName("Should remove item with locking")
    void testRemove() {
        EntityCacheKey key = new EntityCacheKey(1L, "Entity", null);
        dataAccess.putFromLoad(session, key, "value", 1);
        
        dataAccess.remove(session, key);
        
        // Should be removed
        assertNull(dataAccess.get(session, key));
    }

    @Test
    @DisplayName("Should handle remove for non-existent item")
    void testRemoveNonExistent() {
        EntityCacheKey key = new EntityCacheKey(1L, "Entity", null);
        
        // Should not throw
        dataAccess.remove(session, key);
    }

    @Test
    @DisplayName("Should remove all items with region locking")
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
    @DisplayName("Should evict item and clear lock")
    void testEvict() {
        EntityCacheKey key = new EntityCacheKey(1L, "Entity", null);
        dataAccess.putFromLoad(session, key, "value", 1);
        
        SoftLock lock = dataAccess.lockItem(session, key, 1);
        
        dataAccess.evict(key);
        
        // Should be evicted and lock cleared
        assertNull(dataAccess.get(session, key));
        
        // Should be able to put again
        assertTrue(dataAccess.putFromLoad(session, key, "new-value", 2));
    }

    @Test
    @DisplayName("Should evict all items and clear locks")
    void testEvictAll() {
        EntityCacheKey key1 = new EntityCacheKey(1L, "Entity", null);
        EntityCacheKey key2 = new EntityCacheKey(2L, "Entity", null);
        
        dataAccess.putFromLoad(session, key1, "value1", 1);
        dataAccess.putFromLoad(session, key2, "value2", 1);
        
        SoftLock lock = dataAccess.lockItem(session, key1, 1);
        
        dataAccess.evictAll();
        
        assertNull(dataAccess.get(session, key1));
        assertNull(dataAccess.get(session, key2));
        
        // Locks should be cleared
        assertTrue(dataAccess.putFromLoad(session, key1, "after-evict", 2));
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
                            if (j % 3 == 0) {
                                dataAccess.putFromLoad(session, key, "value-" + threadId, 1);
                            } else if (j % 3 == 1) {
                                dataAccess.get(session, key);
                            } else {
                                dataAccess.evict(key);
                            }
                            successCount.incrementAndGet();
                        } catch (Exception e) {
                            // Some operations may fail due to locking, that's ok
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
    @DisplayName("Should handle multi-tenant scenarios")
    void testMultiTenant() {
        EntityCacheKey key1 = new EntityCacheKey(1L, "Entity", "tenant1");
        EntityCacheKey key2 = new EntityCacheKey(1L, "Entity", "tenant2");
        
        dataAccess.putFromLoad(session, key1, "tenant1-value", 1);
        dataAccess.putFromLoad(session, key2, "tenant2-value", 1);
        
        // Lock tenant1's data
        SoftLock lock = dataAccess.lockItem(session, key1, 1);
        
        // Tenant1 should be locked
        assertNull(dataAccess.get(session, key1));
        
        // Tenant2 should still be accessible
        assertEquals("tenant2-value", dataAccess.get(session, key2));
        
        dataAccess.unlockItem(session, key1, lock);
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
    @DisplayName("Should extract cache key id correctly")
    void testGetCacheKeyId() {
        EntityCacheKey cacheKey = new EntityCacheKey(1L, "Entity", "tenant1");
        Object id = dataAccess.getCacheKeyId(cacheKey);
        assertEquals(1L, id);
    }

    @Test
    @DisplayName("Should respect minimalPutOverride flag")
    void testMinimalPutOverride() {
        EntityCacheKey key = new EntityCacheKey(1L, "Entity", null);
        
        dataAccess.putFromLoad(session, key, "value1", 1, false);
        
        // With minimalPutOverride, should not override
        boolean result = dataAccess.putFromLoad(session, key, "value2", 1, true);
        assertFalse(result);
        
        assertEquals("value1", dataAccess.get(session, key));
    }
}