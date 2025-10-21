package com.example.cache.region;

import org.hibernate.cache.CacheException;
import org.hibernate.cache.spi.RegionFactory;
import org.hibernate.cache.spi.TimestampsRegion;
import org.hibernate.engine.spi.SharedSessionContractImplementor;

public class TimestampsRegionImpl implements TimestampsRegion {

  @Override
  public Object getFromCache(Object arg0, SharedSessionContractImplementor arg1) {
    // TODO Auto-generated method stub
    throw new UnsupportedOperationException("Unimplemented method 'getFromCache'");
  }

  @Override
  public void putIntoCache(Object arg0, Object arg1, SharedSessionContractImplementor arg2) {
    // TODO Auto-generated method stub
    throw new UnsupportedOperationException("Unimplemented method 'putIntoCache'");
  }

  @Override
  public void clear() {
    // TODO Auto-generated method stub
    throw new UnsupportedOperationException("Unimplemented method 'clear'");
  }

  @Override
  public void destroy() throws CacheException {
    // TODO Auto-generated method stub
    throw new UnsupportedOperationException("Unimplemented method 'destroy'");
  }

  @Override
  public String getName() {
    // TODO Auto-generated method stub
    throw new UnsupportedOperationException("Unimplemented method 'getName'");
  }

  @Override
  public RegionFactory getRegionFactory() {
    // TODO Auto-generated method stub
    throw new UnsupportedOperationException("Unimplemented method 'getRegionFactory'");
  }
  
}
