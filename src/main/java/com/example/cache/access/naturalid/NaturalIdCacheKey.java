package com.example.cache.access.naturalid;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Objects;

import com.example.cache.utils.CacheKey;

public final class NaturalIdCacheKey implements Serializable, CacheKey {
    private static final long serialVersionUID = 1L;

    private final Object[] naturalIdValues; 
    private final String entityName;
    private final String tenantId;

    public NaturalIdCacheKey(Object[] naturalIdValues, String entityName, String tenantId) {
        if (naturalIdValues == null || naturalIdValues.length == 0)
            throw new IllegalArgumentException("Natural ID values cannot be null or empty");
        if (entityName == null || entityName.trim().isEmpty()) {
            throw new IllegalArgumentException("Entity name cannot be null or empty");
        }
        this.naturalIdValues = naturalIdValues;
        this.entityName = entityName;
        this.tenantId = tenantId;
    }

    public Object[] getNaturalIdValues() {
        return naturalIdValues;
    }

    public String getEntityName() {
        return entityName;
    }

    @Override
    public String getTenantId() {
        return tenantId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof NaturalIdCacheKey)) return false;
        NaturalIdCacheKey that = (NaturalIdCacheKey) o;
        return Arrays.deepEquals(naturalIdValues, that.naturalIdValues)
            && Objects.equals(entityName, that.entityName)
            && Objects.equals(tenantId, that.tenantId);
    }

    @Override
    public int hashCode() {
        int result = Arrays.deepHashCode(naturalIdValues);
        result = 31 * result + Objects.hashCode(entityName);
        result = 31 * result + Objects.hashCode(tenantId);
        return result;
    }

    @Override
    public String toString() {
        return "NaturalIdCacheKey[" + entityName + " " + Arrays.toString(naturalIdValues) + "]";
    }
}
