package com.example.cache.region;

import org.hibernate.cache.CacheException;
import org.hibernate.cache.cfg.spi.DomainDataRegionConfig;
import org.hibernate.cache.spi.*;
import org.hibernate.cache.spi.access.AccessType;
import org.hibernate.cache.spi.access.CollectionDataAccess;
import org.hibernate.cache.spi.access.EntityDataAccess;
import org.hibernate.cache.spi.access.NaturalIdDataAccess;
import org.hibernate.metamodel.model.domain.NavigableRole;

import com.example.cache.access.collections.NoStrictReadWriteCollectionDataAccess;
import com.example.cache.access.collections.ReadOnlyCollectionDataAccess;
import com.example.cache.access.collections.ReadWriteCollectionDataAccess;
import com.example.cache.access.entities.NoStrictReadWriteEntityDataAccess;
import com.example.cache.access.entities.ReadOnlyEntityDataAccess;
import com.example.cache.access.entities.ReadWriteEntityDataAccess;
import com.example.cache.access.naturalid.NoStrictNaturalIdDataAccess;
import com.example.cache.access.naturalid.ReadOnlyNaturalIdDataAccess;
import com.example.cache.access.naturalid.ReadWriteNaturalIdDataAccess;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DomainDataRegionAdapter implements DomainDataRegion {

    private RegionImpl entityRegion;
    private CustomRegionFactory regionFactory;
    private final String regionName;
    private final DomainDataRegionConfig regionConfig;
    private final Map<NavigableRole, EntityDataAccess> entityAccessMap = new ConcurrentHashMap<>();
    private final Map<NavigableRole, CollectionDataAccess> collectionAccessMap = new ConcurrentHashMap<>();
    private final Map<NavigableRole, NaturalIdDataAccess> naturalIdAccessMap = new ConcurrentHashMap<>();
    private volatile boolean destroyed = false;

    public DomainDataRegionAdapter(
            RegionImpl entityRegion, 
            CustomRegionFactory regionFactory,
            DomainDataRegionConfig regionConfig) {
        this.entityRegion = entityRegion;
        this.regionFactory = regionFactory;
        this.regionName = entityRegion.getRegionName();
        this.regionConfig = regionConfig;
    }

    @Override
    public EntityDataAccess getEntityDataAccess(NavigableRole rootEntityRole) {
        if (destroyed) {
            throw new IllegalStateException("Region has been destroyed: " + regionName);
        }
        return entityAccessMap.computeIfAbsent(rootEntityRole, role -> {
            AccessType accessType = determineAccessType(role);
            return createEntityDataAccess(accessType);
        });
    }
    
    @Override
    public CollectionDataAccess getCollectionDataAccess(NavigableRole collectionRole) {
        if (destroyed) {
            throw new IllegalStateException("Region has been destroyed: " + regionName);
        }
        return collectionAccessMap.computeIfAbsent(collectionRole, role -> {
            AccessType accessType = determineAccessType(role);
            return createCollectionDataAccess(accessType);
        });
    }

    @Override
    public NaturalIdDataAccess getNaturalIdDataAccess(NavigableRole rootEntityRole) {
        if (destroyed) {
            throw new IllegalStateException("Region has been destroyed: " + regionName);
        }
        return naturalIdAccessMap.computeIfAbsent(rootEntityRole, role -> {
            AccessType accessType = determineAccessType(role);
            return createNaturalIdDataAccess(accessType);
        });
    }

    private AccessType determineAccessType(NavigableRole role) {
        for (var entityConfig : regionConfig.getEntityCaching()) {
            if (entityConfig.getNavigableRole().equals(role)) {
                return entityConfig.getAccessType();
            }
        }
        return regionFactory.getDefaultAccessType();
    }
    
    private CollectionDataAccess createCollectionDataAccess(AccessType accessType) {
        switch (accessType) {
            case READ_ONLY:
                return new ReadOnlyCollectionDataAccess(entityRegion, this);
            case READ_WRITE:
                return new ReadWriteCollectionDataAccess(entityRegion, this);
            case NONSTRICT_READ_WRITE:
                return new NoStrictReadWriteCollectionDataAccess(entityRegion, this);
            case TRANSACTIONAL:
                throw new UnsupportedOperationException(
                    "TRANSACTIONAL access type requires JTA support - not implemented"
                );
            default:
                throw new CacheException("Unknown access type: " + accessType);
        }
    }

    private EntityDataAccess createEntityDataAccess(AccessType accessType) {
        switch (accessType) {
            case READ_ONLY:
                return new ReadOnlyEntityDataAccess(entityRegion, this);
            case READ_WRITE:
                return new ReadWriteEntityDataAccess(entityRegion, this);
            case NONSTRICT_READ_WRITE:
                return new NoStrictReadWriteEntityDataAccess(entityRegion, this);
            case TRANSACTIONAL:
                throw new UnsupportedOperationException(
                    "TRANSACTIONAL access type requires JTA support - not implemented"
                );
            default:
                throw new CacheException("Unknown access type: " + accessType);
        }
    }

    private NaturalIdDataAccess createNaturalIdDataAccess(AccessType accessType) {
        switch (accessType) {
            case READ_ONLY:
                return new ReadOnlyNaturalIdDataAccess(entityRegion, this);
            case READ_WRITE:
                return new ReadWriteNaturalIdDataAccess(entityRegion, this);
            case NONSTRICT_READ_WRITE:
                return new NoStrictNaturalIdDataAccess(entityRegion, this);
            case TRANSACTIONAL:
                throw new UnsupportedOperationException(
                    "TRANSACTIONAL access type requires JTA support - not implemented"
                );
            default:
                throw new CacheException("Unknown access type: " + accessType);
        }
    }

    @Override
    public void clear() {
        if (!destroyed) {
            entityRegion.evictAll();
        }
    }

    @Override
    public void destroy() throws CacheException {
        if (destroyed) {
            return; 
        }
        
        destroyed = true;
        
        try {

            if (entityRegion != null) {
                entityRegion.evictAll();
            }
            
            entityAccessMap.clear();
            collectionAccessMap.clear();
            naturalIdAccessMap.clear();
            
            if (regionFactory != null) {
                regionFactory.unregisterDomainDataRegion(regionName);
            }
            
            entityRegion = null;
            regionFactory = null;
            
        } catch (Exception e) {
            throw new CacheException("Failed to destroy domain data region: " + regionName, e);
        }
    }

    @Override
    public String getName() {
        return regionName;
    }

    @Override
    public RegionFactory getRegionFactory() {
        return regionFactory;
    }
}