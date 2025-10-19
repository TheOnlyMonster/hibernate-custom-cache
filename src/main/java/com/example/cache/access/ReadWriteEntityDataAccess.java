package com.example.cache.access;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

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
    
    private static final long LOCK_TIMEOUT_MS = 60000; // 1 minute

    public ReadWriteEntityDataAccess(EntityRegionImpl entityRegion, 
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


    private CacheKey toCacheKey(Object key) {
        if (key == null) {
            throw new IllegalArgumentException("Cache key cannot be null");
        }
        if (key instanceof CacheKey) {
            return (CacheKey) key;
        }
        throw new IllegalArgumentException(
            "Expected CacheKey but got: " + key.getClass().getName()
        );
    }

 
    private boolean isLocked(CacheKey cacheKey) {
        if (regionLock != null) {
            if (isLockExpired(regionLock)) {
                regionLock = null; 
            } else {
                return true;
            }
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

    // ========== READ OPERATIONS ==========

    /**
     * Check if an item exists in the cache.
     * Returns false if the item is locked (being updated).
     * 
     * @param key The cache key
     * @return true if item exists and is not locked, false otherwise
     */
    @Override
    public boolean contains(Object key) {
        try {
            CacheKey cacheKey = toCacheKey(key);
            
            if (isLocked(cacheKey)) {
                return false;
            }
            
            return entityRegion.get(cacheKey) != null;
        } catch (Exception e) {
            // Log in production
            return false;
        }
    }

    /**
     * Retrieve an item from cache.
     * Returns null if the item is locked (prevents dirty reads).
     * 
     * @param session The originating session
     * @param key The cache key
     * @return The cached value, or null if not cached or locked
     */
    @Override
    public Object get(SharedSessionContractImplementor session, Object key) {
        try {
            CacheKey cacheKey = toCacheKey(key);
            
            if (isLocked(cacheKey)) {
                return null;
            }
            
            return entityRegion.get(cacheKey);
        } catch (Exception e) {
            // Log in production
            return null;
        }
    }

    /**
     * Cache an object loaded from the database.
     * Only caches if the key is not currently locked.
     * 
     * @param session The originating session
     * @param key The cache key
     * @param value The value to cache
     * @param version The entity version
     * @return true if successfully cached, false otherwise
     */
    @Override
    public boolean putFromLoad(SharedSessionContractImplementor session,
                                Object key,
                                Object value,
                                Object version) {
        return putFromLoad(session, key, value, version, false);
    }

    /**
     * Cache an object with minimal put optimization.
     * 
     * @param session The originating session
     * @param key The cache key
     * @param value The value to cache
     * @param version The entity version
     * @param minimalPutOverride If true, skip if already cached
     * @return true if successfully cached, false otherwise
     */
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
            CacheKey cacheKey = toCacheKey(key);
            
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

    // ========== LOCKING OPERATIONS ==========

    /**
     * Acquire a lock on a cache key before modifying it.
     * This prevents other transactions from reading stale data.
     * 
     * Called before update/delete operations.
     * 
     * @param session The originating session
     * @param key The cache key to lock
     * @param version The current entity version
     * @return A SoftLock if successful, null if region is locked
     * @throws CacheException if the key is already locked
     */
    @Override
    public SoftLock lockItem(SharedSessionContractImplementor session, Object key, Object version) {
        try {
            CacheKey cacheKey = toCacheKey(key);
            
            if (regionLock != null && !isLockExpired(regionLock)) {
                return null;
            }
            
            Object currentValue = entityRegion.get(cacheKey);

            if (currentValue == null) {
                throw new CacheException("Item not found in cache: " + key);
            }

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

    /**
     * Release a lock on a cache key.
     * This does NOT restore the old value - that would undo the update!
     * 
     * Called when a transaction rolls back.
     * 
     * @param session The originating session
     * @param key The cache key to unlock
     * @param lock The lock to release
     */
    @Override
    public void unlockItem(SharedSessionContractImplementor session, Object key, SoftLock lock) {
        if (!(lock instanceof ReadWriteSoftLock)) {
            return;
        }
        
        try {
            ReadWriteSoftLock rwLock = (ReadWriteSoftLock) lock;
            CacheKey cacheKey = rwLock.getKey();
            
            lockMap.remove(cacheKey, rwLock);

            
        } catch (Exception e) {
            // Log in production - don't throw, unlocking should be best-effort
        }
    }

    /**
     * Lock the entire region.
     * Used during bulk operations like removeAll().
     * 
     * @return A region-wide SoftLock
     * @throws CacheException if region is already locked
     */
    @Override
    public SoftLock lockRegion() {
        ReadWriteSoftLock newLock = new ReadWriteSoftLock(null, null, null);
        
        if (regionLock != null && !isLockExpired(regionLock)) {
            throw new CacheException("Region already locked");
        }
        
        regionLock = newLock;
        return newLock;
    }

    /**
     * Unlock the entire region.
     * Clears all individual key locks as well.
     * 
     * @param lock The region lock to release
     */
    @Override
    public void unlockRegion(SoftLock lock) {
        if (lock instanceof ReadWriteSoftLock && lock == regionLock) {
            regionLock = null;
            lockMap.clear(); // Clear all individual locks too
        }
    }

    // ========== WRITE OPERATIONS - INSERT ==========

    /**
     * Called before inserting a new entity.
     * For READ_WRITE, we don't cache here - wait for afterInsert.
     * 
     * @return false (wait for successful insert)
     */
    @Override
    public boolean insert(SharedSessionContractImplementor session,
                        Object key,
                        Object value,
                        Object version) {
        return false;
    }

    /**
     * Called after successfully inserting an entity.
     * Now we can safely cache the new entity.
     * 
     * @param session The originating session
     * @param key The cache key
     * @param value The entity value
     * @param version The entity version
     * @return true if cached, false if region locked
     */
    @Override
    public boolean afterInsert(SharedSessionContractImplementor session,
                             Object key,
                             Object value,
                             Object version) {
        if (value == null) {
            return false;
        }

        try {
            CacheKey cacheKey = toCacheKey(key);
            
            if (regionLock != null && !isLockExpired(regionLock)) {
                return false;
            }
            
            entityRegion.put(cacheKey, value);
            return true;
            
        } catch (Exception e) {
            // Log in production
            return false;
        }
    }

    // ========== WRITE OPERATIONS - UPDATE ==========

    /**
     * Called before updating an entity.
     * Acquires a lock and evicts the old value from cache.
     * 
     * IMPORTANT: The actual cache update happens in afterUpdate()!
     * 
     * @param session The originating session
     * @param key The cache key
     * @param value The new value (not cached yet!)
     * @param currentVersion The new version
     * @param previousVersion The old version
     * @return true if lock acquired, false if region locked
     */
    @Override
    public boolean update(SharedSessionContractImplementor session,
                        Object key,
                        Object value,
                        Object currentVersion,
                        Object previousVersion) {
        try {
            if (regionLock != null && !isLockExpired(regionLock)) {
                return false;
            }
            
            SoftLock lock = lockItem(session, key, previousVersion);
            

            return lock != null;
            
        } catch (Exception e) {
            // Log in production
            return false;
        }
    }

    /**
     * Called after successfully updating an entity.
     * Caches the new value and releases the lock.
     * 
     * This is the critical method that completes the update cycle.
     * 
     * @param session The originating session
     * @param key The cache key
     * @param value The new value to cache
     * @param currentVersion The new version
     * @param previousVersion The old version
     * @param lock The lock from update()
     * @return true if new value cached, false if region locked
     */
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
            CacheKey cacheKey = toCacheKey(key);
            
            if (regionLock != null && !isLockExpired(regionLock)) {
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

    // ========== EVICTION OPERATIONS ==========

    /**
     * Evict a single item from cache.
     * Removes any lock on the key.
     * 
     * @param key The cache key to evict
     */
    @Override
    public void evict(Object key) {
        try {
            CacheKey cacheKey = toCacheKey(key);
            lockMap.remove(cacheKey); 
            entityRegion.evict(cacheKey);
        } catch (Exception e) {
            // Log in production
        }
    }

    /**
     * Evict all items from cache.
     * Clears all locks.
     */
    @Override
    public void evictAll() {
        try {
            lockMap.clear();
            entityRegion.evictAll();
        } catch (Exception e) {
            // Log in production
        }
    }

    /**
     * Remove an item from cache (called during entity deletion).
     * Locks the item, evicts it, then unlocks.
     * 
     * @param session The originating session
     * @param key The cache key to remove
     */
    @Override
    public void remove(SharedSessionContractImplementor session, Object key) {
        try {
            if (regionLock != null && !isLockExpired(regionLock)) {
                return;
            }
            
            CacheKey cacheKey = toCacheKey(key);
            
            SoftLock lock = lockItem(session, key, null);
            
            if (lock != null) {
                try {
                    entityRegion.evict(cacheKey);
                } finally {
                    unlockItem(session, key, lock);
                }
            }
        } catch (Exception e) {
            // Log in production
        }
    }

    /**
     * Remove all items from cache (bulk delete operation).
     * Locks entire region during operation.
     * 
     * @param session The originating session
     */
    @Override
    public void removeAll(SharedSessionContractImplementor session) {
        SoftLock lock = null;
        try {
            lock = lockRegion();
            
            entityRegion.evictAll();
            
        } catch (Exception e) {
            // Log in production
        } finally {
            if (lock != null) {
                unlockRegion(lock);
            }
        }
    }

    // ========== METADATA OPERATIONS ==========

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
        
        return new CacheKey(id, persister.getRootEntityName(), tenantIdentifier);
    }

    @Override
    public Object getCacheKeyId(Object cacheKey) {
        if (cacheKey == null) {
            throw new IllegalArgumentException("Cache key cannot be null");
        }
        if (cacheKey instanceof CacheKey) {
            return ((CacheKey) cacheKey).getId();
        }
        throw new IllegalArgumentException(
            "Unexpected cacheKey type: " + cacheKey.getClass().getName()
        );
    }
}