package com.example.cache.factory;
import com.example.cache.config.CacheConfiguration;
import com.example.cache.metrics.MetricsCollector;
import com.example.cache.region.DomainDataRegionAdapter;
import com.example.cache.region.QueryResultsRegionImpl;
import com.example.cache.region.RegionImpl;
import com.example.cache.region.TimestampsRegionImpl;

import org.hibernate.cache.CacheException;
import org.hibernate.cache.cfg.spi.DomainDataRegionBuildingContext;
import org.hibernate.cache.cfg.spi.DomainDataRegionConfig;
import org.hibernate.cache.spi.*;
import org.hibernate.cache.spi.access.AccessType;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.boot.spi.SessionFactoryOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class CustomRegionFactory implements RegionFactory {

    private static final Logger logger = LoggerFactory.getLogger(CustomRegionFactory.class);
    
    private final AtomicLong nextTimestamp = new AtomicLong();
    private final Map<String, MetricsCollector> metricsMap = new ConcurrentHashMap<>();
    
    private final Map<String, DomainDataRegion> domainDataRegions = new ConcurrentHashMap<>();
    private final Map<String, QueryResultsRegion> queryResultsRegions = new ConcurrentHashMap<>();
    private final Map<String, TimestampsRegion> timestampsRegions = new ConcurrentHashMap<>();
    
    private SessionFactoryOptions settings;
    private CacheConfiguration config;

    @Override
    public void start(SessionFactoryOptions settings, Map<String, Object> configValues) throws CacheException {
        logger.info("Starting CustomRegionFactory with settings: {}", settings);
        this.settings = settings;
        this.config = new CacheConfiguration(configValues);
        this.nextTimestamp.set(System.currentTimeMillis());
        logger.info("CustomRegionFactory started successfully with configuration: {}", config);
    }

    @Override
    public void stop() {
        logger.info("Stopping CustomRegionFactory");

        for (DomainDataRegion region : domainDataRegions.values()) {
            try {
                region.destroy();
            } catch (Exception e) {
                logger.warn("Failed to destroy domain data region: {}", region.getName(), e);
            }
        }
        domainDataRegions.clear();
        
        for (QueryResultsRegion region : queryResultsRegions.values()) {
            try {
                region.destroy();
            } catch (Exception e) {
                logger.warn("Failed to destroy query results region: {}", region.getName(), e);
            }
        }
        queryResultsRegions.clear();
        
        for (TimestampsRegion region : timestampsRegions.values()) {
            try {
                region.destroy();
            } catch (Exception e) {
                logger.warn("Failed to destroy timestamps region: {}", region.getName(), e);
            }
        }
        timestampsRegions.clear();
        
        metricsMap.clear();
        logger.info("CustomRegionFactory stopped successfully");
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
            config.getMaxEntries(),
            config.getTtlMillis(),
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
            config.getMaxEntries(),
            config.getTtlMillis(),
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
            config.getMaxEntries(),
            0, // Timestamps don't need TTL
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
    
    public CacheConfiguration getConfiguration() {
        return config;
    }
}