package com.example.cache.access;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.hibernate.cache.CacheException;
import org.hibernate.cache.spi.DomainDataRegion;
import org.hibernate.cache.spi.access.AccessType;
import org.hibernate.cache.spi.access.EntityDataAccess;
import org.hibernate.cache.spi.access.SoftLock;
import org.hibernate.engine.spi.SessionFactoryImplementor; 
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.persister.entity.EntityPersister;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.cache.region.CacheKey;
import com.example.cache.region.DomainDataRegionAdapter;
import com.example.cache.region.EntityRegionImpl;

public class ReadWriteEntityDataAccess implements EntityDataAccess {

    private static final int LOCK_TIMEOUT_SECONDS = 10;

    private final EntityRegionImpl entityRegion;
    private final DomainDataRegionAdapter domainDataRegion;
    private final ReentrantReadWriteLock readWriteLock;

    public ReadWriteEntityDataAccess(EntityRegionImpl entityRegion, 
                                   DomainDataRegionAdapter domainDataRegion) {
        this.entityRegion = entityRegion;
        this.domainDataRegion = domainDataRegion;
        this.readWriteLock = new ReentrantReadWriteLock();
    }

    @Override
    public boolean contains(Object key) {
        try {
            if (!readWriteLock.readLock().tryLock(LOCK_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                return false;
            }
            try {
                Object value = entityRegion.get(key);
                return value != null && !(value instanceof SoftLock);
            } finally {
                readWriteLock.readLock().unlock();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    @Override
    public Object get(SharedSessionContractImplementor session, Object key) {
        try {
            if (!readWriteLock.readLock().tryLock(LOCK_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                return null;
            }
            try {
                Object value = entityRegion.get(key);
                if (value instanceof SoftLock) {
                    return null;
                }
                return value;
            } finally {
                readWriteLock.readLock().unlock();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    @Override
    public SoftLock lockItem(SharedSessionContractImplementor session, Object key, Object version) {
        try {
            if (!readWriteLock.writeLock().tryLock(LOCK_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw new CacheException("Could not acquire write lock for: " + key);
            }
            try {
                Object current = entityRegion.get(key);
                ReadWriteSoftLock lock = new ReadWriteSoftLock(current, version);
                entityRegion.put(key, lock);
                return lock;
            } finally {
                readWriteLock.writeLock().unlock();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CacheException("Interrupted while locking: " + key, e);
        }
    }

    @Override
    public void unlockItem(SharedSessionContractImplementor session, Object key, SoftLock lock) {
        try {
            if (!readWriteLock.writeLock().tryLock(LOCK_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                return;
            }
            try {
                if (lock instanceof ReadWriteSoftLock) {
                    Object value = ((ReadWriteSoftLock) lock).getActualValue();
                    if (value != null) {
                        entityRegion.put(key, value);
                    } else {
                        entityRegion.evict(key);
                    }
                }
            } finally {
                readWriteLock.writeLock().unlock();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public boolean putFromLoad(SharedSessionContractImplementor session,
                             Object key,
                             Object value,
                             Object txTimestamp,
                             boolean minimalPutOverride) {
        try {
            if (!readWriteLock.writeLock().tryLock(LOCK_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                return false;
            }
            try {
                if (minimalPutOverride && contains(key)) {
                    return false;
                }

                Object current = entityRegion.get(key);
                if (current instanceof SoftLock) {
                    return false;
                }

                entityRegion.put(key, value);
                return true;
            } finally {
                readWriteLock.writeLock().unlock();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    @Override
    public void evict(Object key) {
        try {
            if (!readWriteLock.writeLock().tryLock(LOCK_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                return;
            }
            try {
                entityRegion.evict(key);
            } finally {
                readWriteLock.writeLock().unlock();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void evictAll() {
        try {
            if (!readWriteLock.writeLock().tryLock(LOCK_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                return;
            }
            try {
                entityRegion.evictAll();
            } finally {
                readWriteLock.writeLock().unlock();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public AccessType getAccessType() {
        return AccessType.READ_WRITE;
    }

    @Override
    public DomainDataRegion getRegion() {
        return domainDataRegion;
    }

    @Override
    public Object generateCacheKey(Object id,
                                 EntityPersister persister,
                                 SessionFactoryImplementor factory,
                                 String tenantIdentifier) {
        return new CacheKey(id, persister.getRootEntityName(), tenantIdentifier);
    }

    @Override
    public Object getCacheKeyId(Object cacheKey) {
        if (cacheKey instanceof CacheKey) {
            return ((CacheKey) cacheKey).getId();
        }
        throw new IllegalArgumentException("Unexpected cacheKey type: " + 
            (cacheKey == null ? "null" : cacheKey.getClass().getName()));
    }

    

    @Override
    public boolean insert(SharedSessionContractImplementor session,
                        Object key,
                        Object value,
                        Object version) {
        return false;
    }

    @Override
    public boolean update(SharedSessionContractImplementor session,
                        Object key,
                        Object value,
                        Object currentVersion,
                        Object previousVersion) {
        lockItem(session, key, previousVersion);
        return true;
    }

    @Override
    public boolean putFromLoad(SharedSessionContractImplementor session,
                             Object key,
                             Object value,
                             Object txTimestamp) {
        return putFromLoad(session, key, value, txTimestamp, false);
    }

   

    @Override
    public boolean afterInsert(SharedSessionContractImplementor session,
                             Object key,
                             Object value,
                             Object version) {
        try {
            if (!readWriteLock.writeLock().tryLock(LOCK_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                return false;
            }
            try {
                entityRegion.put(key, value);
                return true;
            } finally {
                readWriteLock.writeLock().unlock();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    @Override
    public boolean afterUpdate(SharedSessionContractImplementor session,
                             Object key,
                             Object value,
                             Object currentVersion,
                             Object previousVersion,
        SoftLock lock) {
      try {
        if (!readWriteLock.writeLock().tryLock(LOCK_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
          return false;
        }
        try {
          unlockItem(session, key, lock);
          entityRegion.put(key, value);
          return true;
        } finally {
          readWriteLock.writeLock().unlock();
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return false;
      }
    }
    
    @Override
    public void remove(SharedSessionContractImplementor session, Object key) {
        evict(key);
    }

    @Override
    public void removeAll(SharedSessionContractImplementor session) {
      evictAll();
    }
    
  @Override
  public SoftLock lockRegion() {
      throw new UnsupportedOperationException("Region-wide locking is not supported.");
  }

  @Override
  public void unlockRegion(SoftLock lock) {
      throw new UnsupportedOperationException("Region-wide unlocking is not supported.");
  }
}