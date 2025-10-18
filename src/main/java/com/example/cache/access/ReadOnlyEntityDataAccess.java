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

  @Override
  public boolean contains(Object arg0) {
    return entityRegion.get(arg0) != null;
  }

  @Override
  public void evict(Object arg0) {
    entityRegion.evict(arg0);
  }

  @Override
  public void evictAll() {
    entityRegion.evictAll();
  }

  @Override
  public Object get(SharedSessionContractImplementor session, Object arg1) {
    return entityRegion.get(arg1);
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
  public SoftLock lockItem(SharedSessionContractImplementor arg0, Object arg1, Object arg2) {
    return null;
  }

  @Override
  public SoftLock lockRegion() {
    return null;
  }

  @Override
  public boolean putFromLoad(SharedSessionContractImplementor session, Object key, Object value, Object txTimestamp) {
    entityRegion.put(key, value);
    return true;
  }

  @Override
  public boolean putFromLoad(SharedSessionContractImplementor session, Object key, Object value, Object txTimestamp,
      boolean minimalPutOverride) {
    if(minimalPutOverride && contains(key)) {
      return false;
    }
    entityRegion.put(key, value);
    return true;
  }

  @Override
  public void remove(SharedSessionContractImplementor session, Object key) {
    // Read-only access does not support remove operation
  }

  @Override
  public void removeAll(SharedSessionContractImplementor session) {
    // Read-only access does not support remove operation
  }
  

  @Override
  public void unlockItem(SharedSessionContractImplementor arg0, Object arg1, SoftLock arg2) {
    // No action needed for read-only access
  }

  @Override
  public void unlockRegion(SoftLock arg0) {
    // No action needed for read-only access
  }

  @Override
  public boolean afterInsert(SharedSessionContractImplementor arg0, Object arg1, Object arg2, Object arg3) {
    // Read-only access does not support afterInsert operation
    return false;
  }

  @Override
  public boolean afterUpdate(SharedSessionContractImplementor arg0, Object arg1, Object arg2, Object arg3, Object arg4,
      SoftLock arg5) {
    // Read-only access does not support afterUpdate operation
    return false;
  }

  @Override
  public Object generateCacheKey(Object id,
                                EntityPersister persister,
                                SessionFactoryImplementor factory,
                                String tenantIdentifier) {

      String entityName = persister.getRootEntityName(); 

      return new CacheKey(id, entityName, tenantIdentifier);
  }

  @Override
  public Object getCacheKeyId(Object cacheKey) {
      if (cacheKey instanceof CacheKey) {
          return ((CacheKey) cacheKey).getId();
      }
      throw new IllegalArgumentException("Unexpected cacheKey type: " + (cacheKey == null ? "null" : cacheKey.getClass()));
  }


  @Override
  public boolean insert(SharedSessionContractImplementor arg0, Object arg1, Object arg2, Object arg3) {
    // Read-only access does not support insert operation
    return false;
  }

  @Override
  public boolean update(SharedSessionContractImplementor arg0, Object arg1, Object arg2, Object arg3, Object arg4) {
    // Read-only access does not support update operation
    return false;
  }
  
}
