package com.example.cache.factory;

import com.example.cache.metrics.MetricsCollector;
import com.example.cache.region.EntityRegionImpl;
import com.example.cache.region.DomainDataRegionAdapter;

import org.hibernate.cache.CacheException;
import org.hibernate.cache.cfg.spi.DomainDataRegionBuildingContext;
import org.hibernate.cache.cfg.spi.DomainDataRegionConfig;
import org.hibernate.cache.spi.*;
import org.hibernate.cache.spi.access.AccessType;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.boot.spi.SessionFactoryOptions;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class CustomRegionFactory implements RegionFactory {

    private final AtomicLong nextTimestamp = new AtomicLong();
    private final Map<String, MetricsCollector> metricsMap = new ConcurrentHashMap<>();
    private SessionFactoryOptions settings;
    
    private static final int DEFAULT_MAX_ENTRIES = 10000;
    private static final long DEFAULT_TTL_MS = 60 * 60 * 1000; 

    @Override
    public void stop() {
        metricsMap.clear();
    }

    @Override
    public void start(SessionFactoryOptions settings, Map<String, Object> configValues) throws CacheException {
        this.settings = settings;
        this.nextTimestamp.set(System.currentTimeMillis());
    }

    @Override
    public boolean isMinimalPutsEnabledByDefault() {
        return true;
    }

    @Override
    public AccessType getDefaultAccessType() {
        return AccessType.READ_WRITE;
    }

    @Override
    public String qualify(String regionName) {
        return settings.getSessionFactoryName() + '.' + regionName;
    }

    @Override
    public long nextTimestamp() {
        return nextTimestamp.incrementAndGet();
    }

    @Override
    public DomainDataRegion buildDomainDataRegion(DomainDataRegionConfig regionConfig,
            DomainDataRegionBuildingContext buildingContext) {
        
        String regionName = regionConfig.getRegionName();
        MetricsCollector metrics = metricsMap.computeIfAbsent(regionName, k -> new MetricsCollector());

        EntityRegionImpl entityRegion = new EntityRegionImpl(
            regionName,
            DEFAULT_MAX_ENTRIES,
            DEFAULT_TTL_MS,
            metrics
        );
        return new DomainDataRegionAdapter(entityRegion, this, null);
    }

    @Override
    public QueryResultsRegion buildQueryResultsRegion(String regionName, SessionFactoryImplementor sessionFactory) {
        throw new UnsupportedOperationException("Query cache not supported");
    }

    @Override
    public TimestampsRegion buildTimestampsRegion(String regionName, SessionFactoryImplementor sessionFactory) {
        throw new UnsupportedOperationException("Timestamps cache not supported"); 
    }
}