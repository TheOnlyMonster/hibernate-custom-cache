package com.example.cache.access.entities;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import org.hibernate.cache.CacheException;
import org.hibernate.cache.spi.DomainDataRegion;
import org.hibernate.cache.spi.access.AccessType;
import org.hibernate.cache.spi.access.EntityDataAccess;
import org.hibernate.cache.spi.access.SoftLock;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.persister.entity.EntityPersister;

import com.example.cache.access.ReadWriteSoftLock;
import com.example.cache.region.DomainDataRegionAdapter;
import com.example.cache.region.RegionImpl;
import com.example.cache.utils.CacheKey;


public class ReadWriteEntityDataAccess implements EntityDataAccess {

    private final RegionImpl entityRegion;
    
    private final DomainDataRegionAdapter domainDataRegion;
    
    private final ConcurrentHashMap<EntityCacheKey, ReadWriteSoftLock> lockMap = new ConcurrentHashMap<>();
    
    private final AtomicReference<ReadWriteSoftLock> regionLock = new AtomicReference<>();
    
    private static final long LOCK_TIMEOUT_MS = 60000; // 1 minute

    public ReadWriteEntityDataAccess(RegionImpl entityRegion, 
                                    DomainDataRegionAdapter domainDataRegion) {
        if (entityRegion == null) {
            throw new IllegalArgumentException("entityRegion cannot be null");
        }
        if (domainDataRegion == null) {
            throw new IllegalArgumentException("domainDataRegion cannot be null");
        }
        this.entityRegion = entityRegion;
        this.domainDataRegion = domainDataRegion;
    }

    
    
    private boolean isRegionLocked() {
        ReadWriteSoftLock currentRegionLock = regionLock.get();
        if (currentRegionLock != null) {
            if (isLockExpired(currentRegionLock)) {
                regionLock.compareAndSet(currentRegionLock, null);
                return regionLock.get() != null;
            } else {
                return true;
            }
        }
        return false;
    }

    private boolean isLocked(EntityCacheKey cacheKey) {
        if (isRegionLocked()) {
            return true;
        }
        
        ReadWriteSoftLock lock = lockMap.get(cacheKey);
        if (lock != null) {
            if (isLockExpired(lock)) {
                lockMap.remove(cacheKey, lock);
                return false;
            }
            return true;
        }
        
        return false;
    }


    private boolean isLockExpired(ReadWriteSoftLock lock) {
        return System.currentTimeMillis() - lock.getTimestamp() > LOCK_TIMEOUT_MS;
    }


    @Override
    public boolean contains(Object key) {
        try {
            EntityCacheKey cacheKey = CacheKey.convert(key, EntityCacheKey.class);
            
            if (isLocked(cacheKey)) {
                return false;
            }
            
            return entityRegion.get(cacheKey) != null;
        } catch (Exception e) {
            // Log in production
            return false;
        }
    }


    @Override
    public Object get(SharedSessionContractImplementor session, Object key) {
        try {
            EntityCacheKey cacheKey = CacheKey.convert(key, EntityCacheKey.class);
            
            if (isLocked(cacheKey)) {
                return null;
            }
            
            return entityRegion.get(cacheKey);
        } catch (Exception e) {
            // Log in production
            return null;
        }
    }


    @Override
    public boolean putFromLoad(SharedSessionContractImplementor session,
                                Object key,
                                Object value,
                                Object version) {
        return putFromLoad(session, key, value, version, false);
    }


    @Override
    public boolean putFromLoad(SharedSessionContractImplementor session,
                                Object key,
                                Object value,
                                Object version,
                                boolean minimalPutOverride) {
        if (value == null) {
            return false;
        }

        try {
            EntityCacheKey cacheKey = CacheKey.convert(key, EntityCacheKey.class);
            
            if (isLocked(cacheKey)) {
                return false;
            }

            if (minimalPutOverride && entityRegion.get(cacheKey) != null) {
                return false;
            }

            entityRegion.put(cacheKey, value);
            return true;
        } catch (Exception e) {
            // Log in production
            return false;
        }
    }


    @Override
    public SoftLock lockItem(SharedSessionContractImplementor session, Object key, Object version) {
        try {
            EntityCacheKey cacheKey = CacheKey.convert(key, EntityCacheKey.class);
            
            if (isRegionLocked()) {
                return null;
            }
            
            Object currentValue = entityRegion.get(cacheKey);

            ReadWriteSoftLock newLock = new ReadWriteSoftLock(cacheKey, currentValue, version);
            
            ReadWriteSoftLock existingLock = lockMap.putIfAbsent(cacheKey, newLock);
            
            if (existingLock != null) {
                
                if (isLockExpired(existingLock)) {

                    if (lockMap.replace(cacheKey, existingLock, newLock)) {
                        entityRegion.evict(cacheKey);
                        return newLock;
                    }
                }

                throw new CacheException("Key already locked: " + key);
            }
            
            // Lock acquired successfully
            entityRegion.evict(cacheKey);
            return newLock;
            
        } catch (CacheException e) {
            throw e;
        } catch (Exception e) {
            throw new CacheException("Failed to lock item: " + key, e);
        }
    }


    @Override
    public void unlockItem(SharedSessionContractImplementor session, Object key, SoftLock lock) {
        if (!(lock instanceof ReadWriteSoftLock)) {
            return;
        }
        
        try {
            ReadWriteSoftLock rwLock = (ReadWriteSoftLock) lock;
            EntityCacheKey cacheKey = CacheKey.convert(rwLock.getKey(), EntityCacheKey.class);
            
            lockMap.remove(cacheKey, rwLock);

            entityRegion.put(cacheKey, rwLock.getOldValue());

            
        } catch (Exception e) {
            // Log in production - don't throw, unlocking should be best-effort
        }
    }

    @Override
    public SoftLock lockRegion() {
        ReadWriteSoftLock newLock = new ReadWriteSoftLock(null, null, null);
        ReadWriteSoftLock existing = regionLock.get();
        
        if (existing != null && !isLockExpired(existing)) {
            throw new CacheException("Region already locked");
        }
        
        if (!regionLock.compareAndSet(existing, newLock)) {
            throw new CacheException("Region already locked by another thread");
        }
        
        return newLock;
    }
    @Override
    public void unlockRegion(SoftLock lock) {
        if (lock instanceof ReadWriteSoftLock) {
            regionLock.compareAndSet((ReadWriteSoftLock) lock, null);
            lockMap.clear();
        }
    }


    @Override
    public boolean insert(SharedSessionContractImplementor session,
                        Object key,
                        Object value,
                        Object version) {
        return false;
    }


    @Override
    public boolean afterInsert(SharedSessionContractImplementor session,
                                Object key,
                                Object value,
                                Object version) {
        if (value == null) {
            return false;
        }

        try {
            EntityCacheKey cacheKey = CacheKey.convert(key, EntityCacheKey.class);
            
            if (isRegionLocked()) {
                return false;
            }
            
            entityRegion.put(cacheKey, value);
            return true;
            
        } catch (Exception e) {
            // Log in production
            return false;
        }
    }


    @Override
    public boolean update(SharedSessionContractImplementor session,
                        Object key,
                        Object value,
                        Object currentVersion,
                        Object previousVersion) {
        try {
            if (isRegionLocked()) {
                return false;
            }
            
            SoftLock lock = lockItem(session, key, previousVersion);
            

            return lock != null;
            
        } catch (Exception e) {
            // Log in production
            return false;
        }
    }


    @Override
    public boolean afterUpdate(SharedSessionContractImplementor session,
                                Object key,
                                Object value,
                                Object currentVersion,
                                Object previousVersion,
                                SoftLock lock) {
        if (value == null) {
            return false;
        }

        try {
            EntityCacheKey cacheKey = CacheKey.convert(key, EntityCacheKey.class);
            
            if (isRegionLocked()) {
                return false;
            }
            
            if (lock instanceof ReadWriteSoftLock) {
                ReadWriteSoftLock rwLock = (ReadWriteSoftLock) lock;
                lockMap.remove(rwLock.getKey(), rwLock);
            }
            
            entityRegion.put(cacheKey, value);
            return true;
            
        } catch (Exception e) {
            // Log in production
            return false;
        }
    }


    @Override
    public void evict(Object key) {
        try {
            EntityCacheKey cacheKey = CacheKey.convert(key, EntityCacheKey.class);
            lockMap.remove(cacheKey); 
            entityRegion.evict(cacheKey);
        } catch (Exception e) {
            // Log in production
        }
    }


    @Override
    public void evictAll() {
        try {
            lockMap.clear();
            entityRegion.evictAll();
        } catch (Exception e) {
            // Log in production
        }
    }


    @Override
    public void remove(SharedSessionContractImplementor session, Object key) {
        try {
            if (isRegionLocked()) {
                return;
            }

            EntityCacheKey cacheKey = CacheKey.convert(key, EntityCacheKey.class);

            SoftLock lock = lockItem(session, cacheKey, null);
            
            if (lock != null) {
                lockMap.remove(cacheKey, lock);
                entityRegion.evict(cacheKey);
            }
        } catch (Exception e) {
            // Log in production
        }
    }


    @Override
    public void removeAll(SharedSessionContractImplementor session) {
        SoftLock lock = null;
        try {
            lock = lockRegion();
            
            entityRegion.evictAll();
            lockMap.clear();
            
        } catch (Exception e) {
            // Log in production
        } finally {
            if (lock != null) {
                unlockRegion(lock);
            }
        }
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
        if (id == null) {
            throw new IllegalArgumentException("Entity id cannot be null");
        }
        if (persister == null) {
            throw new IllegalArgumentException("EntityPersister cannot be null");
        }
        
        return new EntityCacheKey(id, persister.getRootEntityName(), tenantIdentifier);
    }

    @Override
    public Object getCacheKeyId(Object cacheKey) {
        if (cacheKey == null) {
            throw new IllegalArgumentException("Cache key cannot be null");
        }
        if (cacheKey instanceof EntityCacheKey) {
            return ((EntityCacheKey) cacheKey).getId();
        }
        throw new IllegalArgumentException(
            "Unexpected cacheKey type: " + cacheKey.getClass().getName()
        );
    }
}