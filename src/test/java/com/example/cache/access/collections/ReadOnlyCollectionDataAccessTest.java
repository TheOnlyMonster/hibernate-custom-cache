package com.example.cache.access.collections;

import com.example.cache.access.collections.CollectionCacheKey;
import com.example.cache.access.collections.ReadOnlyCollectionDataAccess;
import com.example.cache.metrics.MetricsCollector;
import com.example.cache.region.DomainDataRegionAdapter;
import com.example.cache.region.RegionImpl;
import org.hibernate.cache.spi.access.AccessType;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.persister.collection.CollectionPersister;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("ReadOnlyCollectionDataAccess Tests")
class ReadOnlyCollectionDataAccessTest {

    private ReadOnlyCollectionDataAccess dataAccess;
    private RegionImpl entityRegion;
    private DomainDataRegionAdapter domainDataRegion;
    private SharedSessionContractImplementor session;
    private CollectionPersister persister;
    private SessionFactoryImplementor factory;

    @BeforeEach
    void setUp() {
        MetricsCollector metrics = new MetricsCollector();
        entityRegion = new RegionImpl("test-region", 100, 60000, metrics);
        domainDataRegion = mock(DomainDataRegionAdapter.class);
        dataAccess = new ReadOnlyCollectionDataAccess(entityRegion, domainDataRegion);
        
        session = null;
        persister = mock(CollectionPersister.class);
        factory = null;
        
        when(persister.getRole()).thenReturn("com.example.Entity.orders");
    }

    @Test
    @DisplayName("Should return correct access type")
    void testAccessType() {
        assertEquals(AccessType.READ_ONLY, dataAccess.getAccessType());
    }

    @Test
    @DisplayName("Should generate collection cache key correctly")
    void testGenerateCacheKey() {
        Object key = dataAccess.generateCacheKey(1L, persister, factory, "tenant1");
        
        assertNotNull(key);
        assertTrue(key instanceof CollectionCacheKey);
        CollectionCacheKey cacheKey = (CollectionCacheKey) key;
        assertEquals(1L, cacheKey.getOwnerId());
        assertEquals("com.example.Entity.orders", cacheKey.getRole());
        assertEquals("tenant1", cacheKey.getTenantId());
    }

    @Test
    @DisplayName("Should throw exception for null id")
    void testGenerateCacheKeyNullId() {
        assertThrows(IllegalArgumentException.class, 
            () -> dataAccess.generateCacheKey(null, persister, factory, "tenant1"));
    }

    @Test
    @DisplayName("Should throw exception for null persister")
    void testGenerateCacheKeyNullPersister() {
        assertThrows(IllegalArgumentException.class, 
            () -> dataAccess.generateCacheKey(1L, null, factory, "tenant1"));
    }

    @Test
    @DisplayName("Should extract owner id from cache key")
    void testGetCacheKeyId() {
        CollectionCacheKey cacheKey = new CollectionCacheKey(1L, "role", "tenant1");
        Object id = dataAccess.getCacheKeyId(cacheKey);
        assertEquals(1L, id);
    }

    @Test
    @DisplayName("Should throw exception for null cache key")
    void testGetCacheKeyIdNull() {
        assertThrows(IllegalArgumentException.class, 
            () -> dataAccess.getCacheKeyId(null));
    }

    @Test
    @DisplayName("Should throw exception for wrong cache key type")
    void testGetCacheKeyIdWrongType() {
        assertThrows(IllegalArgumentException.class, 
            () -> dataAccess.getCacheKeyId("not-a-cache-key"));
    }

    @Test
    @DisplayName("Should put and get collection from cache")
    void testPutFromLoadAndGet() {
        CollectionCacheKey key = new CollectionCacheKey(1L, "role", null);
        String value = "collection-data";
        
        boolean result = dataAccess.putFromLoad(session, key, value, 1);
        assertTrue(result);
        
        Object retrieved = dataAccess.get(session, key);
        assertEquals(value, retrieved);
        assertTrue(dataAccess.contains(key));
    }

    @Test
    @DisplayName("Should not put null value")
    void testPutFromLoadNull() {
        CollectionCacheKey key = new CollectionCacheKey(1L, "role", null);
        boolean result = dataAccess.putFromLoad(session, key, null, 1);
        assertFalse(result);
    }

    @Test
    @DisplayName("Should respect minimalPutOverride flag")
    void testPutFromLoadMinimalPutOverride() {
        CollectionCacheKey key = new CollectionCacheKey(1L, "role", null);
        
        dataAccess.putFromLoad(session, key, "value1", 1, false);
        
        boolean result = dataAccess.putFromLoad(session, key, "value2", 1, true);
        assertFalse(result);
        
        assertEquals("value1", dataAccess.get(session, key));
    }

    @Test
    @DisplayName("Should not support locking")
    void testLocking() {
        CollectionCacheKey key = new CollectionCacheKey(1L, "role", null);
        
        assertNull(dataAccess.lockItem(session, key, 1));
        assertNull(dataAccess.lockRegion());
        
        // Should not throw
        dataAccess.unlockItem(session, key, null);
        dataAccess.unlockRegion(null);
    }

    @Test
    @DisplayName("Should evict collection")
    void testEvict() {
        CollectionCacheKey key = new CollectionCacheKey(1L, "role", null);
        dataAccess.putFromLoad(session, key, "value", 1);
        
        dataAccess.evict(key);
        
        assertNull(dataAccess.get(session, key));
        assertFalse(dataAccess.contains(key));
    }

    @Test
    @DisplayName("Should evict all collections")
    void testEvictAll() {
        CollectionCacheKey key1 = new CollectionCacheKey(1L, "role", null);
        CollectionCacheKey key2 = new CollectionCacheKey(2L, "role", null);
        
        dataAccess.putFromLoad(session, key1, "value1", 1);
        dataAccess.putFromLoad(session, key2, "value2", 1);
        
        dataAccess.evictAll();
        
        assertNull(dataAccess.get(session, key1));
        assertNull(dataAccess.get(session, key2));
    }

    @Test
    @DisplayName("Should remove collection")
    void testRemove() {
        CollectionCacheKey key = new CollectionCacheKey(1L, "role", null);
        dataAccess.putFromLoad(session, key, "value", 1);
        
        dataAccess.remove(session, key);
        
        assertNull(dataAccess.get(session, key));
    }

    @Test
    @DisplayName("Should remove all collections")
    void testRemoveAll() {
        CollectionCacheKey key1 = new CollectionCacheKey(1L, "role", null);
        CollectionCacheKey key2 = new CollectionCacheKey(2L, "role", null);
        
        dataAccess.putFromLoad(session, key1, "value1", 1);
        dataAccess.putFromLoad(session, key2, "value2", 1);
        
        dataAccess.removeAll(session);
        
        assertNull(dataAccess.get(session, key1));
        assertNull(dataAccess.get(session, key2));
    }

    @Test
    @DisplayName("Should handle multi-tenant collections")
    void testMultiTenantCollections() {
        CollectionCacheKey key1 = new CollectionCacheKey(1L, "role", "tenant1");
        CollectionCacheKey key2 = new CollectionCacheKey(1L, "role", "tenant2");
        
        dataAccess.putFromLoad(session, key1, "tenant1-collection", 1);
        dataAccess.putFromLoad(session, key2, "tenant2-collection", 1);
        
        assertEquals("tenant1-collection", dataAccess.get(session, key1));
        assertEquals("tenant2-collection", dataAccess.get(session, key2));
    }

    @Test
    @DisplayName("Should handle different collection roles")
    void testDifferentRoles() {
        CollectionCacheKey key1 = new CollectionCacheKey(1L, "Entity.orders", null);
        CollectionCacheKey key2 = new CollectionCacheKey(1L, "Entity.items", null);
        
        dataAccess.putFromLoad(session, key1, "orders-collection", 1);
        dataAccess.putFromLoad(session, key2, "items-collection", 1);
        
        assertEquals("orders-collection", dataAccess.get(session, key1));
        assertEquals("items-collection", dataAccess.get(session, key2));
    }
}