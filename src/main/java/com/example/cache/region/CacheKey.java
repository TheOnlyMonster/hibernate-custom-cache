package com.example.cache.region;

import java.io.Serializable;
import java.util.Objects;

public final class CacheKey implements Serializable {
    private static final long serialVersionUID = 1L;

    private final Object id;
    private final String entityName;
    private final String tenantId;

    public CacheKey(Object id, String entityName, String tenantId) {
        this.id = id;
        this.entityName = entityName;
        this.tenantId = tenantId;
    }

    public Object getId() {
        return id;
    }

    public String getEntityName() {
        return entityName;
    }

    public String getTenantId() {
        return tenantId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CacheKey)) return false;
        CacheKey other = (CacheKey) o;
        return Objects.equals(id, other.id)
            && Objects.equals(entityName, other.entityName)
            && Objects.equals(tenantId, other.tenantId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, entityName, tenantId);
    }

    @Override
    public String toString() {
        return "CacheKey[" + entityName + "#" + id + (tenantId != null ? ", tenant=" + tenantId : "") + "]";
    }
}
