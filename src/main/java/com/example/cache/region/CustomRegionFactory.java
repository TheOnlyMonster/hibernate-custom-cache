package com.example.cache.region;
import com.example.cache.metrics.MetricsCollector;

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
    
    private final Map<String, DomainDataRegion> domainDataRegions = new ConcurrentHashMap<>();
    private final Map<String, QueryResultsRegion> queryResultsRegions = new ConcurrentHashMap<>();
    private final Map<String, TimestampsRegion> timestampsRegions = new ConcurrentHashMap<>();
    
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

        for (DomainDataRegion region : domainDataRegions.values()) {
            try {
                region.destroy();
            } catch (Exception e) {
                // Log but continue cleanup
            }
        }
        domainDataRegions.clear();
        
        for (QueryResultsRegion region : queryResultsRegions.values()) {
            try {
                region.destroy();
            } catch (Exception e) {
                // Log but continue cleanup
            }
        }
        queryResultsRegions.clear();
        
        for (TimestampsRegion region : timestampsRegions.values()) {
            try {
                region.destroy();
            } catch (Exception e) {
                // Log but continue cleanup
            }
        }
        timestampsRegions.clear();
        
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
        
        DomainDataRegionAdapter adapter = new DomainDataRegionAdapter(
            entityRegion, 
            this, 
            regionConfig
        );
        
        domainDataRegions.put(regionName, adapter);
        
        return adapter;
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
        
        QueryResultsRegionImpl queryResultsRegion = new QueryResultsRegionImpl(
            this, 
            queryRegion
        );
        
        queryResultsRegions.put(regionName, queryResultsRegion);
        
        return queryResultsRegion;
    }

    @Override
    public TimestampsRegion buildTimestampsRegion(
            String regionName, 
            SessionFactoryImplementor sessionFactory) {
        
        RegionImpl timestampsStorage = new RegionImpl(
            regionName,
            DEFAULT_MAX_ENTRIES,
            0, 
            metricsMap.computeIfAbsent(
                regionName, 
                k -> new MetricsCollector()
            )
        );
        
        TimestampsRegionImpl timestampsRegion = new TimestampsRegionImpl(
            this, 
            timestampsStorage
        );
        
        timestampsRegions.put(regionName, timestampsRegion);
        
        return timestampsRegion;
    }
    
    public void unregisterDomainDataRegion(String regionName) {
        domainDataRegions.remove(regionName);
        metricsMap.remove(regionName);
    }
    
    public void unregisterQueryResultsRegion(String regionName) {
        queryResultsRegions.remove(regionName);
        metricsMap.remove(regionName);
    }
    
    public void unregisterTimestampsRegion(String regionName) {
        timestampsRegions.remove(regionName);
        metricsMap.remove(regionName);
    }

    public MetricsCollector getMetrics(String regionName) {
        return metricsMap.get(regionName);
    }

    public Map<String, MetricsCollector> getAllMetrics() {
        return new ConcurrentHashMap<>(metricsMap);
    }
}