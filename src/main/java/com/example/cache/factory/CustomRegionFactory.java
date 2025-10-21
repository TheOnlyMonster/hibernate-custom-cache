package com.example.cache.factory;

import com.example.cache.metrics.MetricsCollector;
import com.example.cache.region.RegionImpl;
import com.example.cache.region.DomainDataRegionAdapter;
import com.example.cache.region.QueryResultsRegionImpl;

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
    public void start(SessionFactoryOptions settings, Map<String, Object> configValues) throws CacheException {
        this.settings = settings;
        this.nextTimestamp.set(System.currentTimeMillis());
    }

    @Override
    public void stop() {
        metricsMap.clear();
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
        String sessionFactoryName = settings.getSessionFactoryName();
        if (sessionFactoryName == null) {
            sessionFactoryName = "default";
        }
        return sessionFactoryName + '.' + regionName;
    }

    @Override
    public long nextTimestamp() {
        return nextTimestamp.incrementAndGet();
    }

    @Override
    public DomainDataRegion buildDomainDataRegion(
            DomainDataRegionConfig regionConfig,
            DomainDataRegionBuildingContext buildingContext) {
        
        String regionName = regionConfig.getRegionName();
        
        MetricsCollector metrics = metricsMap.computeIfAbsent(
            regionName, 
            k -> new MetricsCollector()
        );

        RegionImpl entityRegion = new RegionImpl(
            regionName,
            DEFAULT_MAX_ENTRIES,
            DEFAULT_TTL_MS,
            metrics
        );
        
        return new DomainDataRegionAdapter(entityRegion, this, regionConfig);
    }

    @Override
    public QueryResultsRegion buildQueryResultsRegion(
            String regionName, 
            SessionFactoryImplementor sessionFactory) {
        RegionImpl queryRegion = new RegionImpl(
            regionName,
            DEFAULT_MAX_ENTRIES,
            DEFAULT_TTL_MS,
            metricsMap.computeIfAbsent(
                regionName, 
                k -> new MetricsCollector()
            )
        );
        return new QueryResultsRegionImpl(this, queryRegion);
    }

    @Override
    public TimestampsRegion buildTimestampsRegion(
            String regionName, 
            SessionFactoryImplementor sessionFactory) {
        throw new UnsupportedOperationException(
            "Timestamps cache not supported. Region: " + regionName
        );
    }
    

    public MetricsCollector getMetrics(String regionName) {
        return metricsMap.get(regionName);
    }
    

    public Map<String, MetricsCollector> getAllMetrics() {
        return new ConcurrentHashMap<>(metricsMap);
    }
}