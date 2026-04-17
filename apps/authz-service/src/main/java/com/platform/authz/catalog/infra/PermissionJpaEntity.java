package com.platform.authz.catalog.infra;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "permission")
public class PermissionJpaEntity {

    @Id
    private UUID id;

    @Column(name = "module_id", nullable = false)
    private UUID moduleId;

    @Column(nullable = false, length = 128)
    private String code;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false, length = 16)
    @Enumerated(EnumType.STRING)
    private com.platform.authz.catalog.domain.PermissionStatus status;

    @Column(name = "sunset_at")
    private Instant sunsetAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PermissionJpaEntity() {
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getModuleId() {
        return moduleId;
    }

    public void setModuleId(UUID moduleId) {
        this.moduleId = moduleId;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public com.platform.authz.catalog.domain.PermissionStatus getStatus() {
        return status;
    }

    public void setStatus(com.platform.authz.catalog.domain.PermissionStatus status) {
        this.status = status;
    }

    public Instant getSunsetAt() {
        return sunsetAt;
    }

    public void setSunsetAt(Instant sunsetAt) {
        this.sunsetAt = sunsetAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
