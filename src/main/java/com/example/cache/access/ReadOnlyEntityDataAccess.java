package com.example.cache.access;

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


public class ReadOnlyEntityDataAccess implements EntityDataAccess {

  private final EntityRegionImpl entityRegion;
  private final DomainDataRegionAdapter domainDataRegion;

  public ReadOnlyEntityDataAccess(EntityRegionImpl entityRegion, DomainDataRegionAdapter domainDataRegion) {
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

  /**
   * Check if an item is in the cache.
   * 
   * @param key The cache key (must be a CacheKey instance)
   * @return true if the item exists in cache, false otherwise
   */
  @Override
  public boolean contains(Object key) {
    try {
      CacheKey cacheKey = toCacheKey(key);
      return entityRegion.get(cacheKey) != null;
    } catch (Exception e) {
      return false;
    }
  }

  /**
   * Retrieve an item from the cache.
   * 
   * @param session The originating session
   * @param key The cache key
   * @return The cached object, or null if not found
   */
  @Override
  public Object get(SharedSessionContractImplementor session, Object key) {
    try {
      CacheKey cacheKey = toCacheKey(key);
      return entityRegion.get(cacheKey);
    } catch (Exception e) {
      return null;
    }
  }

  /**
   * Attempt to cache an object loaded from the database.
   * This is called after loading from DB to populate the cache.
   * 
   * @param session The originating session
   * @param key The cache key
   * @param value The value to cache
   * @param version The version of the entity (unused in READ_ONLY)
   * @return true if the value was cached, false otherwise
   */
  @Override
  public boolean putFromLoad(SharedSessionContractImplementor session, Object key, Object value, Object version) {
    return putFromLoad(session, key, value, version, false);
  }

  /**
   * Attempt to cache an object with minimal put optimization.
   * 
   * @param session The originating session
   * @param key The cache key
   * @param value The value to cache
   * @param version The version (unused in READ_ONLY)
   * @param minimalPutOverride If true, skip put if key already exists
   * @return true if the value was cached, false otherwise
   */
  @Override
  public boolean putFromLoad(
      SharedSessionContractImplementor session, 
      Object key, 
      Object value, 
      Object version,
      boolean minimalPutOverride) {
    
    if (value == null) {
      return false;
    }

    try {
      CacheKey cacheKey = toCacheKey(key);
      
      if (minimalPutOverride && entityRegion.get(cacheKey) != null) {
        return false;
      }

      entityRegion.put(cacheKey, value);
      return true;
    } catch (Exception e) {
      return false;
    }
  }

  /**
   * Evict a single item from the cache.
   * Typically called when the entity is deleted or when manually invalidating cache.
   * 
   * @param key The cache key to evict
   */
  @Override
  public void evict(Object key) {
    try {
      CacheKey cacheKey = toCacheKey(key);
      entityRegion.evict(cacheKey);
    } catch (Exception e) {
      // Log error in production  
    }
  }

  /**
   * Evict all items from this cache region.
   * Typically called when doing bulk operations or manual cache clearing.
   */
  @Override
  public void evictAll() {
    try {
      entityRegion.evictAll();
    } catch (Exception e) {
      // Log error in production
    }
  }

  /**
   * Remove an item from cache (same as evict for READ_ONLY).
   * Called during entity deletion.
   * 
   * @param session The originating session
   * @param key The cache key to remove
   */
  @Override
  public void remove(SharedSessionContractImplementor session, Object key) {
    evict(key);
  }

  /**
   * Remove all items from cache (same as evictAll for READ_ONLY).
   * Called during bulk delete operations.
   * 
   * @param session The originating session
   */
  @Override
  public void removeAll(SharedSessionContractImplementor session) {
    evictAll();
  }

  /**
   * Returns the access type of this strategy.
   * 
   * @return AccessType.READ_ONLY
   */
  @Override
  public AccessType getAccessType() {
    return AccessType.READ_ONLY;
  }

  /**
   * Get the parent domain data region.
   * 
   * @return The DomainDataRegion this access strategy belongs to
   */
  @Override
  public DomainDataRegion getRegion() {
    return domainDataRegion;
  }

  // ========== LOCKING OPERATIONS (No-ops for READ_ONLY) ==========
  
  /**
   * Lock an item in the cache. Not needed for READ_ONLY strategy.
   * 
   * @return null (no lock needed for immutable data)
   */
  @Override
  public SoftLock lockItem(SharedSessionContractImplementor session, Object key, Object version) {
    // READ_ONLY data is immutable, no locking needed
    return null;
  }

  /**
   * Lock the entire region. Not needed for READ_ONLY strategy.
   * 
   * @return null (no lock needed for immutable data)
   */
  @Override
  public SoftLock lockRegion() {
    // READ_ONLY data is immutable, no locking needed
    return null;
  }

  /**
   * Unlock an item. Not needed for READ_ONLY strategy.
   */
  @Override
  public void unlockItem(SharedSessionContractImplementor session, Object key, SoftLock lock) {
    // No-op: READ_ONLY has no locks
  }

  /**
   * Unlock the entire region. Not needed for READ_ONLY strategy.
   */
  @Override
  public void unlockRegion(SoftLock lock) {
    // No-op: READ_ONLY has no locks
  }

  // ========== WRITE OPERATIONS (Return false for READ_ONLY) ==========

  /**
   * Called before inserting a new entity.
   * For READ_ONLY, we don't cache on insert - only on subsequent loads.
   * 
   * @return false (don't cache on insert)
   */
  @Override
  public boolean insert(
      SharedSessionContractImplementor session, 
      Object key, 
      Object value, 
      Object version) {
    // READ_ONLY caches are only populated via putFromLoad after query
    // We don't cache directly on insert to avoid stale data if insert fails
    return false;
  }

  /**
   * Called after successfully inserting an entity.
   * For READ_ONLY, we could cache here, but it's safer to let putFromLoad handle it.
   * 
   * @return false (let natural loading populate cache)
   */
  @Override
  public boolean afterInsert(
      SharedSessionContractImplementor session, 
      Object key, 
      Object value, 
      Object version) {
    
    return false;
  }

  /**
   * Called before updating an entity.
   * For READ_ONLY, this should never happen - entities should be immutable.
   * If called, we evict the entry to maintain consistency.
   * 
   * @return false (updates not supported, evict entry)
   */
  @Override
  public boolean update(
      SharedSessionContractImplementor session, 
      Object key, 
      Object value, 
      Object currentVersion, 
      Object previousVersion) {
    evict(key);
    return false;
  }

  /**
   * Called after updating an entity.
   * For READ_ONLY, this should never happen. Evict the entry.
   * 
   * @return false (updates not supported)
   */
  @Override
  public boolean afterUpdate(
      SharedSessionContractImplementor session, 
      Object key, 
      Object value, 
      Object currentVersion, 
      Object previousVersion, 
      SoftLock lock) {
    evict(key);
    return false;
  }

  // ========== CACHE KEY OPERATIONS ==========

  /**
   * Generate a cache key from entity id and metadata.
   * 
   * @param id The entity identifier
   * @param persister The entity persister
   * @param factory The session factory
   * @param tenantIdentifier The tenant identifier (null if not multi-tenant)
   * @return A CacheKey instance
   */
  @Override
  public Object generateCacheKey(
      Object id,
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

  /**
   * Extract the entity id from a cache key.
   * 
   * @param cacheKey The cache key
   * @return The entity identifier
   * @throws IllegalArgumentException if cacheKey is not a CacheKey instance
   */
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