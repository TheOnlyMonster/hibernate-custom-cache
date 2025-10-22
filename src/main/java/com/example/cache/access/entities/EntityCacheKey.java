package com.example.cache.access.entities;

import java.io.Serializable;
import java.util.Objects;

import com.example.cache.utils.CacheKey;


public final class EntityCacheKey implements Serializable, CacheKey {
    private static final long serialVersionUID = 1L;

    private final Object id;
    private final String entityName;
    private final String tenantId;

    public EntityCacheKey(Object id, String entityName, String tenantId) {
        if (id == null) {
            throw new IllegalArgumentException("Entity ID cannot be null");
        }
        if (entityName == null || entityName.trim().isEmpty()) {
            throw new IllegalArgumentException("Entity name cannot be null or empty");
        }
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
    @Override
    public String getTenantId() {
        return tenantId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof EntityCacheKey)) return false;
        EntityCacheKey other = (EntityCacheKey) o;
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
        return "EntityCacheKey[" + entityName + "#" + id + (tenantId != null ? ", tenant=" + tenantId : "") + "]";
    }
}
