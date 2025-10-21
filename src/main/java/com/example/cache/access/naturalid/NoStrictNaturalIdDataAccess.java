package com.example.cache.access.naturalid;

import org.hibernate.cache.spi.DomainDataRegion;
import org.hibernate.cache.spi.access.AccessType;
import org.hibernate.cache.spi.access.NaturalIdDataAccess;
import org.hibernate.cache.spi.access.SoftLock;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.persister.entity.EntityPersister;

import com.example.cache.region.DomainDataRegionAdapter;
import com.example.cache.region.RegionImpl;
import com.example.cache.utils.CacheKey;

public class NoStrictNaturalIdDataAccess implements NaturalIdDataAccess {
    private final RegionImpl entityRegion;
    private final DomainDataRegionAdapter domainDataRegion;


    public NoStrictNaturalIdDataAccess(RegionImpl entityRegion, DomainDataRegionAdapter domainDataRegion) {
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
          NaturalIdCacheKey cacheKey = CacheKey.convert(key, NaturalIdCacheKey.class);

            return entityRegion.get(cacheKey) != null;
        } catch (Exception e) {
            // Log in production
            return false;
        }
    }

    @Override
    public void evict(Object key) {
      try {
            NaturalIdCacheKey cacheKey = CacheKey.convert(key, NaturalIdCacheKey.class);
            entityRegion.evict(cacheKey);
        } catch (Exception e) {
            // Log in production
        }
    }

    @Override
    public void evictAll() {
        try {
            entityRegion.evictAll();
        } catch (Exception e) {
            // Log in production
        }
    }

    @Override
    public Object get(SharedSessionContractImplementor session, Object key) {
      try {
          NaturalIdCacheKey cacheKey = CacheKey.convert(key, NaturalIdCacheKey.class);
            return entityRegion.get(cacheKey);
        } catch (Exception e) {
            // Log in production
            return null;
        }
    }

    @Override
    public AccessType getAccessType() {
        return AccessType.NONSTRICT_READ_WRITE;
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
            NaturalIdCacheKey cacheKey = CacheKey.convert(key, NaturalIdCacheKey.class);
            

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
    public void remove(SharedSessionContractImplementor session, Object key) {
        try {
            NaturalIdCacheKey cacheKey = CacheKey.convert(key, NaturalIdCacheKey.class);
            entityRegion.evict(cacheKey);
        } catch (Exception e) {
            // Log in production
        }
    }

    @Override
    public void removeAll(SharedSessionContractImplementor session) {
        try {
            entityRegion.evictAll();
            
        } catch (Exception e) {
            // Log in production
        } 
    }

    @Override
    public void unlockItem(SharedSessionContractImplementor arg0, Object arg1, SoftLock arg2) {
        // No-op
    }

    @Override
    public void unlockRegion(SoftLock arg0) {
        // No-op
    }

    @Override
    public boolean afterInsert(SharedSessionContractImplementor session,
                                Object key,
                                Object value) {
        if (value == null) {
            return false;
        }

        try {
          NaturalIdCacheKey cacheKey = CacheKey.convert(key, NaturalIdCacheKey.class);
            
            entityRegion.put(cacheKey, value);
            return true;
            
        } catch (Exception e) {
            // Log in production
            return false;
        }
    }

    @Override
    public boolean afterUpdate(SharedSessionContractImplementor session,
                                Object key,
                                Object value,
                                SoftLock lock) {
        if (value == null) {
            return false;
        }

        try {
          NaturalIdCacheKey cacheKey = CacheKey.convert(key, NaturalIdCacheKey.class);
            
            entityRegion.put(cacheKey, value);
            return true;
            
        } catch (Exception e) {
            // Log in production
            return false;
        }
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
    public boolean insert(SharedSessionContractImplementor session,
                        Object key,
                        Object value) {
        return false;
    }

    @Override
    public boolean update(SharedSessionContractImplementor session,
                        Object key,
                        Object value) {
      try {
          NaturalIdCacheKey cacheKey = CacheKey.convert(key, NaturalIdCacheKey.class);
            entityRegion.evict(cacheKey);  
        } catch (Exception e) {
            // Log
        }
        return false;
    }

    @Override
    public Object getNaturalIdValues(Object arg0) {
      NaturalIdCacheKey cacheKey = CacheKey.convert(arg0, NaturalIdCacheKey.class);
      return cacheKey.getNaturalIdValues();
    }

}
