package com.example.cache.region;

import org.hibernate.cache.CacheException;
import org.hibernate.cache.spi.*;
import org.hibernate.cache.spi.access.AccessType;
import org.hibernate.cache.spi.access.CollectionDataAccess;
import org.hibernate.cache.spi.access.EntityDataAccess;
import org.hibernate.cache.spi.access.NaturalIdDataAccess;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.metamodel.model.domain.NavigableRole;

import com.example.cache.factory.CustomRegionFactory;


public class DomainDataRegionAdapter implements DomainDataRegion {

  private final EntityRegionImpl entityRegion;
  private final CustomRegionFactory regionFactory;
  private final EntityDataAccess entityDataAccess;

  public DomainDataRegionAdapter(EntityRegionImpl entityRegion, CustomRegionFactory regionFactory, EntityDataAccess entityDataAccess) {
      this.entityRegion = entityRegion;
      this.regionFactory = regionFactory;
      this.entityDataAccess = entityDataAccess;
  }

  @Override
  public void clear() {
    entityRegion.evictAll();
  }

  @Override
  public void destroy() throws CacheException {
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

  @Override
  public CollectionDataAccess getCollectionDataAccess(NavigableRole arg0) {
    throw new UnsupportedOperationException("Unimplemented method 'getCollectionDataAccess'");
  }

  @Override
  public EntityDataAccess getEntityDataAccess(NavigableRole arg0) {
    return entityDataAccess;
  }

  @Override
  public NaturalIdDataAccess getNaturalIdDataAccess(NavigableRole arg0) {
    throw new UnsupportedOperationException("Unimplemented method 'getNaturalIdDataAccess'");
  }
    
  
}
