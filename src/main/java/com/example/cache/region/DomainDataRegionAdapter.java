package com.example.cache.region;

import org.hibernate.cache.CacheException;
import org.hibernate.cache.cfg.spi.DomainDataRegionConfig;
import org.hibernate.cache.spi.*;
import org.hibernate.cache.spi.access.AccessType;
import org.hibernate.cache.spi.access.CollectionDataAccess;
import org.hibernate.cache.spi.access.EntityDataAccess;
import org.hibernate.cache.spi.access.NaturalIdDataAccess;
import org.hibernate.metamodel.model.domain.NavigableRole;

import com.example.cache.access.ReadOnlyEntityDataAccess;
import com.example.cache.access.ReadWriteEntityDataAccess;
import com.example.cache.factory.CustomRegionFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DomainDataRegionAdapter implements DomainDataRegion {

    private final EntityRegionImpl entityRegion;
    private final CustomRegionFactory regionFactory;
    private final DomainDataRegionConfig regionConfig;
    private final Map<NavigableRole, EntityDataAccess> entityAccessMap = new ConcurrentHashMap<>();
    private final Map<NavigableRole, CollectionDataAccess> collectionAccessMap = new ConcurrentHashMap<>();
    private final Map<NavigableRole, NaturalIdDataAccess> naturalIdAccessMap = new ConcurrentHashMap<>();

    public DomainDataRegionAdapter(
            EntityRegionImpl entityRegion, 
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
                throw new UnsupportedOperationException(
                    "READ_ONLY access type for collections not yet implemented"
                );
                
            case READ_WRITE:
                throw new UnsupportedOperationException(
                    "READ_WRITE access type for collections not yet implemented"
                );
                
            case NONSTRICT_READ_WRITE:
                throw new UnsupportedOperationException(
                    "NONSTRICT_READ_WRITE access type for collections not yet implemented"
                );
                
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
                throw new UnsupportedOperationException(
                    "NONSTRICT_READ_WRITE access type not yet implemented"
                );
                
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
                throw new UnsupportedOperationException(
                    "READ_ONLY access type for NaturalId not yet implemented"
                );
                
            case READ_WRITE:
                throw new UnsupportedOperationException(
                    "READ_WRITE access type for NaturalId not yet implemented"
                );
                
            case NONSTRICT_READ_WRITE:
                throw new UnsupportedOperationException(
                    "NONSTRICT_READ_WRITE access type for NaturalId not yet implemented"
                );
                
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