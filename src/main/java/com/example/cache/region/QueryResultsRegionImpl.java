package com.example.cache.region;

import org.hibernate.cache.CacheException;
import org.hibernate.cache.spi.QueryResultsRegion;
import org.hibernate.cache.spi.RegionFactory;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import com.example.cache.factory.CustomRegionFactory;

public class QueryResultsRegionImpl implements QueryResultsRegion {
  private final CustomRegionFactory regionFactory;
  private final RegionImpl queryRegion; 

  public QueryResultsRegionImpl(CustomRegionFactory regionFactory, RegionImpl queryRegion) {
    this.regionFactory = regionFactory;
    this.queryRegion = queryRegion;
  }

  @Override
  public Object getFromCache(Object arg0, SharedSessionContractImplementor arg1) {
    return queryRegion.get(arg0);
  }

  @Override
  public void putIntoCache(Object arg0, Object arg1, SharedSessionContractImplementor arg2) {
    queryRegion.put(arg0, arg1);
  }

  @Override
  public void clear() {
    queryRegion.evictAll();
  }

  @Override
  public void destroy() throws CacheException {
    queryRegion.evictAll();
  }

  @Override
  public String getName() {
    return queryRegion.getRegionName();
  }

  @Override
  public RegionFactory getRegionFactory() {
    return regionFactory;
  }
  
}
