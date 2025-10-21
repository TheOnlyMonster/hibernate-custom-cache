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
import com.example.cache.factory.CustomRegionFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DomainDataRegionAdapter implements DomainDataRegion {

    private final RegionImpl entityRegion;
    private final CustomRegionFactory regionFactory;
    private final DomainDataRegionConfig regionConfig;
    private final Map<NavigableRole, EntityDataAccess> entityAccessMap = new ConcurrentHashMap<>();
    private final Map<NavigableRole, CollectionDataAccess> collectionAccessMap = new ConcurrentHashMap<>();
    private final Map<NavigableRole, NaturalIdDataAccess> naturalIdAccessMap = new ConcurrentHashMap<>();

    public DomainDataRegionAdapter(
            RegionImpl entityRegion, 
            CustomRegionFactory regionFactory,
            DomainDataRegionConfig regionConfig) {
        this.entityRegion = entityRegion;
        this.regionFactory = regionFactory;
        this.regionConfig = regionConfig;
    }

    @Override
    public EntityDataAccess getEntityDataAccess(NavigableRole rootEntityRole) {
        return entityAccessMap.computeIfAbsent(rootEntityRole, role -> {
            AccessType accessType = determineAccessType(role);
            return createEntityDataAccess(accessType);
        });
    }
    
    @Override
    public CollectionDataAccess getCollectionDataAccess(NavigableRole collectionRole) {
        return collectionAccessMap.computeIfAbsent(collectionRole, role -> {
            AccessType accessType = determineAccessType(role);
            return createCollectionDataAccess(accessType);
        });
    }
    
    

    @Override
    public NaturalIdDataAccess getNaturalIdDataAccess(NavigableRole rootEntityRole) {
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
                // TODO: Implement transactional access
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
                // TODO: Implement transactional access
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
        entityRegion.evictAll();
    }

    @Override
    public void destroy() throws CacheException {
        entityAccessMap.clear();
        entityRegion.evictAll();
    }

    @Override
    public String getName() {
        return entityRegion.getRegionName();
    }

    @Override
    public RegionFactory getRegionFactory() {
        return regionFactory;
    }
}