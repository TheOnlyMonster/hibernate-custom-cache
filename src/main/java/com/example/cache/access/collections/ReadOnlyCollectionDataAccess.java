package com.example.cache.access.collections;

import org.hibernate.cache.spi.DomainDataRegion;
import org.hibernate.cache.spi.access.AccessType;
import org.hibernate.cache.spi.access.CollectionDataAccess;
import org.hibernate.cache.spi.access.SoftLock;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.persister.collection.CollectionPersister;

import com.example.cache.region.DomainDataRegionAdapter;
import com.example.cache.region.EntityRegionImpl;
import com.example.cache.utils.CustomUtils;

public class ReadOnlyCollectionDataAccess implements CollectionDataAccess {

  private final EntityRegionImpl entityRegion;
  private final DomainDataRegionAdapter domainDataRegion;

  public ReadOnlyCollectionDataAccess(EntityRegionImpl entityRegion, DomainDataRegionAdapter domainDataRegion) {
    if (entityRegion == null) {
      throw new IllegalArgumentException("entityRegion cannot be null");
    }
    if (domainDataRegion == null) {
      throw new IllegalArgumentException("domainDataRegion cannot be null");
    }
    this.entityRegion = entityRegion;
    this.domainDataRegion = domainDataRegion;
  }


  @Override
  public boolean contains(Object key) {
    try {
      CollectionCacheKey cacheKey = CustomUtils.toCacheKey(key, CollectionCacheKey.class);
      return entityRegion.get(cacheKey) != null;
    } catch (Exception e) {
      return false;
    }
  }


  @Override
  public Object get(SharedSessionContractImplementor session, Object key) {
    try {
      CollectionCacheKey cacheKey = CustomUtils.toCacheKey(key, CollectionCacheKey.class);
      return entityRegion.get(cacheKey);
    } catch (Exception e) {
      return null;
    }
  }


  @Override
  public boolean putFromLoad(SharedSessionContractImplementor session, Object key, Object value, Object version) {
    return putFromLoad(session, key, value, version, false);
  }


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
      CollectionCacheKey cacheKey = CustomUtils.toCacheKey(key, CollectionCacheKey.class);
      
      if (minimalPutOverride && entityRegion.get(cacheKey) != null) {
        return false;
      }

      entityRegion.put(cacheKey, value);
      return true;
    } catch (Exception e) {
      return false;
    }
  }


  @Override
  public void evict(Object key) {
    try {
      CollectionCacheKey cacheKey = CustomUtils.toCacheKey(key, CollectionCacheKey.class);
      entityRegion.evict(cacheKey);
    } catch (Exception e) {
      // Log error in production  
    }
  }


  @Override
  public void evictAll() {
    try {
      entityRegion.evictAll();
    } catch (Exception e) {
      // Log error in production
    }
  }


  @Override
  public void remove(SharedSessionContractImplementor session, Object key) {
    evict(key);
  }


  @Override
  public void removeAll(SharedSessionContractImplementor session) {
    evictAll();
  }


  @Override
  public AccessType getAccessType() {
    return AccessType.READ_ONLY;
  }


  @Override
  public DomainDataRegion getRegion() {
    return domainDataRegion;
  }


  @Override
  public SoftLock lockItem(SharedSessionContractImplementor session, Object key, Object version) {
    // READ_ONLY data is immutable, no locking needed
    return null;
  }


  @Override
  public SoftLock lockRegion() {
    // READ_ONLY data is immutable, no locking needed
    return null;
  }


  @Override
  public void unlockItem(SharedSessionContractImplementor session, Object key, SoftLock lock) {
    // No-op: READ_ONLY has no locks
  }


  @Override
  public void unlockRegion(SoftLock lock) {
    // No-op: READ_ONLY has no locks
  }


  @Override
  public Object generateCacheKey(
      Object id,
      CollectionPersister persister,
      SessionFactoryImplementor factory,
      String tenantIdentifier) {
    
    if (id == null) {
      throw new IllegalArgumentException("Entity id cannot be null");
    }
    if (persister == null) {
      throw new IllegalArgumentException("EntityPersister cannot be null");
    }

    return new CollectionCacheKey(id, persister.getRole(), tenantIdentifier);
  }


  @Override
  public Object getCacheKeyId(Object cacheKey) {
    if (cacheKey == null) {
      throw new IllegalArgumentException("Cache key cannot be null");
    }
    if (cacheKey instanceof CollectionCacheKey) {
      return ((CollectionCacheKey) cacheKey).getOwnerId();
    }
    throw new IllegalArgumentException(
      "Unexpected cacheKey type: " + cacheKey.getClass().getName()
    );
  }


}
