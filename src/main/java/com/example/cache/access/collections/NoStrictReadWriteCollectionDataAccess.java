package com.example.cache.access.collections;

import org.hibernate.cache.spi.DomainDataRegion;
import org.hibernate.cache.spi.access.AccessType;
import org.hibernate.cache.spi.access.CollectionDataAccess;
import org.hibernate.cache.spi.access.SoftLock;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.persister.collection.CollectionPersister;

public class NoStrictReadWriteCollectionDataAccess implements CollectionDataAccess {

  @Override
  public boolean contains(Object arg0) {
    // TODO Auto-generated method stub
    throw new UnsupportedOperationException("Unimplemented method 'contains'");
  }

  @Override
  public void evict(Object arg0) {
    // TODO Auto-generated method stub
    throw new UnsupportedOperationException("Unimplemented method 'evict'");
  }

  @Override
  public void evictAll() {
    // TODO Auto-generated method stub
    throw new UnsupportedOperationException("Unimplemented method 'evictAll'");
  }

  @Override
  public Object get(SharedSessionContractImplementor arg0, Object arg1) {
    // TODO Auto-generated method stub
    throw new UnsupportedOperationException("Unimplemented method 'get'");
  }

  @Override
  public AccessType getAccessType() {
    // TODO Auto-generated method stub
    throw new UnsupportedOperationException("Unimplemented method 'getAccessType'");
  }

  @Override
  public DomainDataRegion getRegion() {
    // TODO Auto-generated method stub
    throw new UnsupportedOperationException("Unimplemented method 'getRegion'");
  }

  @Override
  public SoftLock lockItem(SharedSessionContractImplementor arg0, Object arg1, Object arg2) {
    // TODO Auto-generated method stub
    throw new UnsupportedOperationException("Unimplemented method 'lockItem'");
  }

  @Override
  public SoftLock lockRegion() {
    // TODO Auto-generated method stub
    throw new UnsupportedOperationException("Unimplemented method 'lockRegion'");
  }

  @Override
  public boolean putFromLoad(SharedSessionContractImplementor arg0, Object arg1, Object arg2, Object arg3) {
    // TODO Auto-generated method stub
    throw new UnsupportedOperationException("Unimplemented method 'putFromLoad'");
  }

  @Override
  public boolean putFromLoad(SharedSessionContractImplementor arg0, Object arg1, Object arg2, Object arg3,
      boolean arg4) {
    // TODO Auto-generated method stub
    throw new UnsupportedOperationException("Unimplemented method 'putFromLoad'");
  }

  @Override
  public void remove(SharedSessionContractImplementor arg0, Object arg1) {
    // TODO Auto-generated method stub
    throw new UnsupportedOperationException("Unimplemented method 'remove'");
  }

  @Override
  public void removeAll(SharedSessionContractImplementor arg0) {
    // TODO Auto-generated method stub
    throw new UnsupportedOperationException("Unimplemented method 'removeAll'");
  }

  @Override
  public void unlockItem(SharedSessionContractImplementor arg0, Object arg1, SoftLock arg2) {
    // TODO Auto-generated method stub
    throw new UnsupportedOperationException("Unimplemented method 'unlockItem'");
  }

  @Override
  public void unlockRegion(SoftLock arg0) {
    // TODO Auto-generated method stub
    throw new UnsupportedOperationException("Unimplemented method 'unlockRegion'");
  }

  @Override
  public Object generateCacheKey(Object arg0, CollectionPersister arg1, SessionFactoryImplementor arg2, String arg3) {
    // TODO Auto-generated method stub
    throw new UnsupportedOperationException("Unimplemented method 'generateCacheKey'");
  }

  @Override
  public Object getCacheKeyId(Object arg0) {
    // TODO Auto-generated method stub
    throw new UnsupportedOperationException("Unimplemented method 'getCacheKeyId'");
  }
  
}
