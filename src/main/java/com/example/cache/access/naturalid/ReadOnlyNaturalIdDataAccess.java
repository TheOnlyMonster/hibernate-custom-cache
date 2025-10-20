package com.example.cache.access.naturalid;

import org.hibernate.cache.spi.DomainDataRegion;
import org.hibernate.cache.spi.access.AccessType;
import org.hibernate.cache.spi.access.NaturalIdDataAccess;
import org.hibernate.cache.spi.access.SoftLock;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.persister.entity.EntityPersister;

import com.example.cache.region.DomainDataRegionAdapter;
import com.example.cache.region.EntityRegionImpl;
import com.example.cache.utils.CustomUtils;

public class ReadOnlyNaturalIdDataAccess implements NaturalIdDataAccess {
  private final EntityRegionImpl entityRegion;
  private final DomainDataRegionAdapter domainDataRegion;

  public ReadOnlyNaturalIdDataAccess(EntityRegionImpl entityRegion, DomainDataRegionAdapter domainDataRegion) {
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
      NaturalIdCacheKey cacheKey = CustomUtils.toCacheKey(key, NaturalIdCacheKey.class);
      return entityRegion.get(cacheKey) != null;
    } catch (Exception e) {
      return false;
    }
  }


  @Override
  public Object get(SharedSessionContractImplementor session, Object key) {
    try {
      NaturalIdCacheKey cacheKey = CustomUtils.toCacheKey(key, NaturalIdCacheKey.class);
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
      NaturalIdCacheKey cacheKey = CustomUtils.toCacheKey(key, NaturalIdCacheKey.class);
      
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
      NaturalIdCacheKey cacheKey = CustomUtils.toCacheKey(key, NaturalIdCacheKey.class);
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
  public boolean afterInsert(
      SharedSessionContractImplementor session, 
      Object key, 
      Object value) {
    return false;
  }


  @Override
  public boolean afterUpdate(
      SharedSessionContractImplementor session, 
      Object key, 
      Object value,
      SoftLock lock) {
    evict(key);
    return false;
  }


  @Override
  public Object generateCacheKey(
      Object naturalIdValues,
      EntityPersister persister,
      SharedSessionContractImplementor session) {
      
      if (naturalIdValues == null) {
          throw new IllegalArgumentException("Natural ID values cannot be null");
      }
      if (persister == null) {
          throw new IllegalArgumentException("EntityPersister cannot be null");
      }
      
      Object[] valuesArray;
      if (naturalIdValues instanceof Object[]) {
          valuesArray = (Object[]) naturalIdValues;
      } else {
          valuesArray = new Object[] { naturalIdValues };
      }
      
      return new NaturalIdCacheKey(
          valuesArray,
          persister.getRootEntityName(),
          session.getTenantIdentifier()
      );
  }
  @Override
  public Object getNaturalIdValues(Object arg0) {
    NaturalIdCacheKey cacheKey = CustomUtils.toCacheKey(arg0, NaturalIdCacheKey.class);
    return cacheKey.getNaturalIdValues();
  }
  @Override
  public boolean insert(
      SharedSessionContractImplementor session, 
      Object key, 
      Object value) {
    return false;
  }

  @Override
  public boolean update(
      SharedSessionContractImplementor session, 
      Object key, 
      Object value) {
    evict(key);
    return false;
  }


}
