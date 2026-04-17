package com.platform.authz.catalog.infra;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "sync_event")
public class SyncEventJpaEntity {

    @Id
    private UUID id;

    @Column(name = "module_id", nullable = false)
    private UUID moduleId;

    @Column(name = "payload_hash", nullable = false, length = 128)
    private String payloadHash;

    @Column(name = "schema_version", nullable = false, length = 32)
    private String schemaVersion;

    @Column(name = "permission_count", nullable = false)
    private int permissionCount;

    @Column(nullable = false)
    private int added;

    @Column(nullable = false)
    private int updated;

    @Column(nullable = false)
    private int deprecated;

    @Column(name = "catalog_version", nullable = false, length = 64)
    private String catalogVersion;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected SyncEventJpaEntity() {
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

    public String getPayloadHash() {
        return payloadHash;
    }

    public void setPayloadHash(String payloadHash) {
        this.payloadHash = payloadHash;
    }

    public String getSchemaVersion() {
        return schemaVersion;
    }

    public void setSchemaVersion(String schemaVersion) {
        this.schemaVersion = schemaVersion;
    }

    public int getPermissionCount() {
        return permissionCount;
    }

    public void setPermissionCount(int permissionCount) {
        this.permissionCount = permissionCount;
    }

    public int getAdded() {
        return added;
    }

    public void setAdded(int added) {
        this.added = added;
    }

    public int getUpdated() {
        return updated;
    }

    public void setUpdated(int updated) {
        this.updated = updated;
    }

    public int getDeprecated() {
        return deprecated;
    }

    public void setDeprecated(int deprecated) {
        this.deprecated = deprecated;
    }

    public String getCatalogVersion() {
        return catalogVersion;
    }

    public void setCatalogVersion(String catalogVersion) {
        this.catalogVersion = catalogVersion;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(Instant occurredAt) {
        this.occurredAt = occurredAt;
    }
}
