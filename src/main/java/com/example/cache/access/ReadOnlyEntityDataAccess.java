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
    return entityRegion.get(toCacheKey(key)) != null;
  }

  @Override
  public void evict(Object key) {
    entityRegion.evict(toCacheKey(key));
  }

  @Override
  public void evictAll() {
    entityRegion.evictAll();
  }

  @Override
  public Object get(SharedSessionContractImplementor session, Object key) {
    return entityRegion.get(toCacheKey(key));
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
    return null;
  }

  @Override
  public SoftLock lockRegion() {
    return null;
  }

  @Override
  public boolean putFromLoad(SharedSessionContractImplementor session, Object key, Object value, Object version) {
    entityRegion.put(toCacheKey(key), value);
    return true;
  }

  @Override
  public boolean putFromLoad(SharedSessionContractImplementor session, Object key, Object value, Object version,
      boolean minimalPutOverride) {
    if(minimalPutOverride && contains(key)) {
      return false;
    }
    entityRegion.put(toCacheKey(key), value);
    return true;
  }

  @Override
  public void remove(SharedSessionContractImplementor session, Object key) {
    entityRegion.evict(toCacheKey(key));
  }

  @Override
  public void removeAll(SharedSessionContractImplementor session) {
    entityRegion.evictAll();
  }

  @Override
  public void unlockItem(SharedSessionContractImplementor session, Object key, SoftLock lock) {
  }

  @Override
  public void unlockRegion(SoftLock lock) {
  }

  @Override
  public boolean afterInsert(SharedSessionContractImplementor session, Object key, Object value, Object version) {
    return false;
  }

  @Override
  public boolean afterUpdate(SharedSessionContractImplementor session, Object key, Object value, Object currentVersion, Object previousVersion,
      SoftLock lock) {
    return false;
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
      throw new IllegalArgumentException("Unexpected cacheKey type: " + (cacheKey == null ? "null" : cacheKey.getClass()));
  }

  @Override
  public boolean insert(SharedSessionContractImplementor session, Object key, Object value, Object version) {
    return false;
  }

  @Override
  public boolean update(SharedSessionContractImplementor session, Object key, Object value, Object currentVersion, Object previousVersion) {
    return false;
  }
  
}