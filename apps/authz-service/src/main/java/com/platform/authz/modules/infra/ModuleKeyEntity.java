package com.platform.authz.modules.infra;

import com.platform.authz.modules.domain.ModuleKeyStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "module_key")
public class ModuleKeyEntity {

    @Id
    private UUID id;

    @Column(name = "module_id", nullable = false)
    private UUID moduleId;

    @Column(name = "key_hash", nullable = false)
    private String keyHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ModuleKeyStatus status;

    @Column(name = "rotated_at")
    private Instant rotatedAt;

    @Column(name = "grace_expires_at")
    private Instant graceExpiresAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ModuleKeyEntity() {
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

    public String getKeyHash() {
        return keyHash;
    }

    public void setKeyHash(String keyHash) {
        this.keyHash = keyHash;
    }

    public ModuleKeyStatus getStatus() {
        return status;
    }

    public void setStatus(ModuleKeyStatus status) {
        this.status = status;
    }

    public Instant getRotatedAt() {
        return rotatedAt;
    }

    public void setRotatedAt(Instant rotatedAt) {
        this.rotatedAt = rotatedAt;
    }

    public Instant getGraceExpiresAt() {
        return graceExpiresAt;
    }

    public void setGraceExpiresAt(Instant graceExpiresAt) {
        this.graceExpiresAt = graceExpiresAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
