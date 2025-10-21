package com.example.cache.access.entities;

import org.hibernate.cache.spi.DomainDataRegion;
import org.hibernate.cache.spi.access.AccessType;
import org.hibernate.cache.spi.access.EntityDataAccess;
import org.hibernate.cache.spi.access.SoftLock;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.persister.entity.EntityPersister;

import com.example.cache.region.DomainDataRegionAdapter;
import com.example.cache.region.RegionImpl;
import com.example.cache.utils.CacheKey;

public class NoStrictReadWriteEntityDataAccess implements EntityDataAccess {

    private final RegionImpl entityRegion;
    private final DomainDataRegionAdapter domainDataRegion;


    public NoStrictReadWriteEntityDataAccess(RegionImpl entityRegion, DomainDataRegionAdapter domainDataRegion) {
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
            EntityCacheKey cacheKey = CacheKey.convert(key, EntityCacheKey.class);

            return entityRegion.get(cacheKey) != null;
        } catch (Exception e) {
            // Log in production
            return false;
        }
    }

    @Override
    public void evict(Object key) {
        try {
            EntityCacheKey cacheKey = CacheKey.convert(key, EntityCacheKey.class);
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
            EntityCacheKey cacheKey = CacheKey.convert(key, EntityCacheKey.class);
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
            EntityCacheKey cacheKey = CacheKey.convert(key, EntityCacheKey.class);
            

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
            EntityCacheKey cacheKey = CacheKey.convert(key, EntityCacheKey.class);
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
                                Object value,
                                Object version) {
        if (value == null) {
            return false;
        }

        try {
            EntityCacheKey cacheKey = CacheKey.convert(key, EntityCacheKey.class);
            
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
                                Object currentVersion,
                                Object previousVersion,
                                SoftLock lock) {
        if (value == null) {
            return false;
        }

        try {
            EntityCacheKey cacheKey = CacheKey.convert(key, EntityCacheKey.class);
            
            entityRegion.put(cacheKey, value);
            return true;
            
        } catch (Exception e) {
            // Log in production
            return false;
        }
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


    @Override
    public boolean insert(SharedSessionContractImplementor session,
                        Object key,
                        Object value,
                        Object version) {
        return false;
    }

    @Override
    public boolean update(SharedSessionContractImplementor session,
                        Object key,
                        Object value,
                        Object currentVersion,
                        Object previousVersion) {
        try {
            EntityCacheKey cacheKey = CacheKey.convert(key, EntityCacheKey.class);
            entityRegion.evict(cacheKey);  
        } catch (Exception e) {
            // Log
        }
        return false;
    }

}
