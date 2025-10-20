package com.example.cache.access.collections;

import java.io.Serializable;
import java.util.Objects;

import com.example.cache.utils.CacheKey;

public final class CollectionCacheKey implements Serializable, CacheKey {
    private static final long serialVersionUID = 1L;
    
    private final Object ownerId;        
    private final String role;           
    private final String tenantId;       
    

    public CollectionCacheKey(Object ownerId, String role, String tenantId) {
        if (ownerId == null) {
            throw new IllegalArgumentException("Owner ID cannot be null");
        }
        if (role == null || role.trim().isEmpty()) {
            throw new IllegalArgumentException("Role cannot be null or empty");
        }
        this.ownerId = ownerId;
        this.role = role;
        this.tenantId = tenantId;
    }
    

    public Object getOwnerId() {
        return ownerId;
    }
    

    public String getRole() {
        return role;
    }
    

    @Override
    public String getTenantId() {
        return tenantId;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CollectionCacheKey)) return false;
        
        CollectionCacheKey that = (CollectionCacheKey) o;
        
        return Objects.equals(ownerId, that.ownerId)
            && Objects.equals(role, that.role)
            && Objects.equals(tenantId, that.tenantId);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(ownerId, role, tenantId);
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("CollectionCacheKey[");
        sb.append(role);
        sb.append("#");
        sb.append(ownerId);
        if (tenantId != null) {
            sb.append(", tenant=");
            sb.append(tenantId);
        }
        sb.append("]");
        return sb.toString();
    }
}