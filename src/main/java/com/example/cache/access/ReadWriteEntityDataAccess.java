package com.example.cache.access;

import java.util.concurrent.ConcurrentHashMap;

import org.hibernate.cache.CacheException;
import org.hibernate.cache.spi.DomainDataRegion;
import org.hibernate.cache.spi.access.AccessType;
import org.hibernate.cache.spi.access.EntityDataAccess;
import org.hibernate.cache.spi.access.SoftLock;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.persister.entity.EntityPersister;

import com.example.cache.region.CacheKey;
import com.example.cache.region.DomainDataRegionAdapter;
import com.example.cache.region.EntityRegionImpl;

public class ReadWriteEntityDataAccess implements EntityDataAccess {

    private final EntityRegionImpl entityRegion;
    private final DomainDataRegionAdapter domainDataRegion;
    private final ConcurrentHashMap<CacheKey, ReadWriteSoftLock> lockMap = new ConcurrentHashMap<>();
    private volatile ReadWriteSoftLock regionLock;

    public ReadWriteEntityDataAccess(EntityRegionImpl entityRegion, 
                                   DomainDataRegionAdapter domainDataRegion) {
        this.entityRegion = entityRegion;
        this.domainDataRegion = domainDataRegion;
    }


    private CacheKey toCacheKey(Object key) {
        if (key instanceof CacheKey) {
            return (CacheKey) key;
        }
        throw new IllegalArgumentException(
            "Expected CacheKey but got: " + (key == null ? "null" : key.getClass().getName())
        );
    }

    @Override
    public boolean contains(Object key) {
        CacheKey cacheKey = toCacheKey(key);
        if (regionLock != null || lockMap.containsKey(cacheKey)) {
            return false;
        }
        return entityRegion.get(cacheKey) != null;
    }

    @Override
    public Object get(SharedSessionContractImplementor session, Object key) {
        CacheKey cacheKey = toCacheKey(key);
        if (regionLock != null || lockMap.containsKey(cacheKey)) {
            return null;
        }
        return entityRegion.get(cacheKey);
    }

    @Override
    public SoftLock lockItem(SharedSessionContractImplementor session, Object key, Object version) {
        if (regionLock != null) {
            throw new CacheException("Region is locked, cannot lock individual item: " + key);
        }
        
        CacheKey cacheKey = toCacheKey(key);
        Object current = entityRegion.get(cacheKey);
        ReadWriteSoftLock lock = new ReadWriteSoftLock(cacheKey, current, version);
        ReadWriteSoftLock existing = lockMap.putIfAbsent(cacheKey, lock);
        
        if (existing != null) {
            throw new CacheException("Key already locked: " + key);
        }
        
        entityRegion.evict(cacheKey);
        return lock;
    }

    @Override
    public void unlockItem(SharedSessionContractImplementor session, Object key, SoftLock lock) {
        if (!(lock instanceof ReadWriteSoftLock)) {
            return;
        }
        
        ReadWriteSoftLock rwLock = (ReadWriteSoftLock) lock;
        CacheKey cacheKey = rwLock.getKey();
        lockMap.remove(cacheKey);
        
        Object oldValue = rwLock.getOldValue();
        if (oldValue != null) {
            entityRegion.put(cacheKey, oldValue);
        }
    }

    @Override
    public boolean putFromLoad(SharedSessionContractImplementor session,
                             Object key,
                             Object value,
                             Object version,
                             boolean minimalPutOverride) {
        CacheKey cacheKey = toCacheKey(key);
        
        if (regionLock != null || lockMap.containsKey(cacheKey)) {
            return false;
        }

        if (minimalPutOverride && entityRegion.get(cacheKey) != null) {
            return false;
        }

        entityRegion.put(cacheKey, value);
        return true;
    }

    @Override
    public void evict(Object key) {
        CacheKey cacheKey = toCacheKey(key);
        lockMap.remove(cacheKey);
        entityRegion.evict(cacheKey);
    }

    @Override
    public void evictAll() {
        lockMap.clear();
        entityRegion.evictAll();
    }

    @Override
    public AccessType getAccessType() {
        return AccessType.READ_WRITE;
    }

    @Override
    public DomainDataRegion getRegion() {
        return domainDataRegion;
    }

    @Override
    public Object generateCacheKey(Object id,
                                 EntityPersister persister,
                                 SessionFactoryImplementor factory,
                                 String tenantIdentifier) {
        return new CacheKey(id, persister.getRootEntityName(), tenantIdentifier);
    }

    @Override
    public Object getCacheKeyId(Object cacheKey) {
        if (cacheKey instanceof CacheKey) {
            return ((CacheKey) cacheKey).getId();
        }
        throw new IllegalArgumentException("Unexpected cacheKey type: " + 
            (cacheKey == null ? "null" : cacheKey.getClass().getName()));
    }

    @Override
    public boolean insert(SharedSessionContractImplementor session,
                        Object key,
                        Object value,
                        Object version) {
        if (regionLock != null) {
            return false;
        }
        return false;
    }

    @Override
    public boolean update(SharedSessionContractImplementor session,
                        Object key,
                        Object value,
                        Object currentVersion,
                        Object previousVersion) {
        if (regionLock != null) {
            return false;
        }
        
        SoftLock lock = lockItem(session, key, previousVersion);
        return lock != null;
    }

    @Override
    public boolean putFromLoad(SharedSessionContractImplementor session,
                             Object key,
                             Object value,
                             Object version) {
        return putFromLoad(session, key, value, version, false);
    }

    @Override
    public boolean afterInsert(SharedSessionContractImplementor session,
                             Object key,
                             Object value,
                             Object version) {
        if (regionLock != null) {
            return false;
        }
        
        CacheKey cacheKey = toCacheKey(key);
        entityRegion.put(cacheKey, value);
        return true;
    }

    @Override
    public boolean afterUpdate(SharedSessionContractImplementor session,
                             Object key,
                             Object value,
                             Object currentVersion,
                             Object previousVersion,
                             SoftLock lock) {
        if (regionLock != null) {
            return false;
        }
        
        if (lock instanceof ReadWriteSoftLock) {
            ReadWriteSoftLock rwLock = (ReadWriteSoftLock) lock;
            lockMap.remove(rwLock.getKey());
        }
        
        CacheKey cacheKey = toCacheKey(key);
        entityRegion.put(cacheKey, value);
        return true;
    }
    
    @Override
    public void remove(SharedSessionContractImplementor session, Object key) {
        if (regionLock != null) {
            return;
        }
        
        CacheKey cacheKey = toCacheKey(key);
        SoftLock lock = lockItem(session, key, null);
        if (lock != null) {
            entityRegion.evict(cacheKey);
            unlockItem(session, key, lock);
        }
    }

    @Override
    public void removeAll(SharedSessionContractImplementor session) {
        SoftLock lock = lockRegion();
        try {
            entityRegion.evictAll();
        } finally {
            unlockRegion(lock);
        }
    }
    
    @Override
    public SoftLock lockRegion() {
        ReadWriteSoftLock lock = new ReadWriteSoftLock(null, null, null);
        
        if (regionLock != null) {
            throw new CacheException("Region already locked");
        }
        
        regionLock = lock;
        
        return lock;
    }

    @Override
    public void unlockRegion(SoftLock lock) {
        if (lock instanceof ReadWriteSoftLock && lock == regionLock) {
            regionLock = null;
            lockMap.clear();
        }
    }
}