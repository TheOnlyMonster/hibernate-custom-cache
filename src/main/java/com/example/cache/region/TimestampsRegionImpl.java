package com.example.cache.region;

import org.hibernate.cache.CacheException;
import org.hibernate.cache.spi.RegionFactory;
import org.hibernate.cache.spi.TimestampsRegion;
import org.hibernate.engine.spi.SharedSessionContractImplementor;

import com.example.cache.factory.CustomRegionFactory;

public class TimestampsRegionImpl implements TimestampsRegion {

  private CustomRegionFactory regionFactory;
  private RegionImpl timestampsRegion;
  private final String regionName;
  private volatile boolean destroyed = false;

  public TimestampsRegionImpl(CustomRegionFactory regionFactory, RegionImpl timestampsRegion) {
    this.regionFactory = regionFactory;
    this.timestampsRegion = timestampsRegion;
    this.regionName = timestampsRegion.getRegionName();
  }

  @Override
  public Object getFromCache(Object key, SharedSessionContractImplementor session) {
    if (destroyed) {
      return null;
    }
    return timestampsRegion.get(key);
  }

  @Override
  public void putIntoCache(Object key, Object value, SharedSessionContractImplementor session) {
    if (destroyed) {
      return;
    }
    timestampsRegion.put(key, value);
  }

  @Override
  public void clear() {
    if (!destroyed) {
      timestampsRegion.evictAll();
    }
  }

  @Override
  public void destroy() throws CacheException {
    if (destroyed) {
      return; 
    }
    
    destroyed = true;
    
    try {

      if (timestampsRegion != null) {
        timestampsRegion.evictAll();
      }
      
      if (regionFactory != null) {
        regionFactory.unregisterTimestampsRegion(regionName);
      }
      
      timestampsRegion = null;
      regionFactory = null;
      
    } catch (Exception e) {
      throw new CacheException("Failed to destroy timestamps region: " + regionName, e);
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