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

public class NoStrictReadWriteCollectionDataAccess implements CollectionDataAccess {

    private final EntityRegionImpl entityRegion;
    private final DomainDataRegionAdapter domainDataRegion;


    public NoStrictReadWriteCollectionDataAccess(EntityRegionImpl entityRegion, DomainDataRegionAdapter domainDataRegion) {
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
            // Log in production
            return false;
        }
    }

    @Override
    public void evict(Object key) {
        try {
            CollectionCacheKey cacheKey = CustomUtils.toCacheKey(key, CollectionCacheKey.class);
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
            CollectionCacheKey cacheKey = CustomUtils.toCacheKey(key, CollectionCacheKey.class);
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
            CollectionCacheKey cacheKey = CustomUtils.toCacheKey(key, CollectionCacheKey.class);
            

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
            CollectionCacheKey cacheKey = CustomUtils.toCacheKey(key, CollectionCacheKey.class);
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
    public Object generateCacheKey(Object id,
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
