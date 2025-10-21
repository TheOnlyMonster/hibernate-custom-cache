package com.example.cache.region;


import org.hibernate.cache.CacheException;
import org.hibernate.cache.spi.QueryResultsRegion;
import org.hibernate.cache.spi.RegionFactory;
import org.hibernate.engine.spi.SharedSessionContractImplementor;

public class QueryResultsRegionImpl implements QueryResultsRegion {
  
  private CustomRegionFactory regionFactory;
  private RegionImpl queryRegion;
  private final String regionName;
  private volatile boolean destroyed = false;

  public QueryResultsRegionImpl(CustomRegionFactory regionFactory, RegionImpl queryRegion) {
    this.regionFactory = regionFactory;
    this.queryRegion = queryRegion;
    this.regionName = queryRegion.getRegionName();
  }

  @Override
  public Object getFromCache(Object key, SharedSessionContractImplementor session) {
    if (destroyed) {
      return null;
    }
    return queryRegion.get(key);
  }

  @Override
  public void putIntoCache(Object key, Object value, SharedSessionContractImplementor session) {
    if (destroyed) {
      return;
    }
    queryRegion.put(key, value);
  }

  @Override
  public void clear() {
    if (!destroyed) {
      queryRegion.evictAll();
    }
  }

  @Override
  public void destroy() throws CacheException {
    if (destroyed) {
      return; 
    }
    
    destroyed = true;
    
    try {
      if (queryRegion != null) {
        queryRegion.evictAll();
      }
      
      if (regionFactory != null) {
        regionFactory.unregisterQueryResultsRegion(regionName);
      }
      
      queryRegion = null;
      regionFactory = null;
      
    } catch (Exception e) {
      throw new CacheException("Failed to destroy query results region: " + regionName, e);
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