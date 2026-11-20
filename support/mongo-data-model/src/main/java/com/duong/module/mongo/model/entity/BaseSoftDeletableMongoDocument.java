package com.duong.module.mongo.model.entity;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.Transient;

import java.time.Instant;
import java.util.Objects;

/**
 * Optional deletion metadata; authorization, persistence and retention belong to consumers.
 */
@Getter
@NoArgsConstructor
@SuperBuilder
public abstract class BaseSoftDeletableMongoDocument extends BaseMongoDocument {
    private Instant deletedAt;
    private String deletedBy;

    @Transient
    public boolean isDeleted() {
        return deletedAt != null;
    }

    /**
     * Repeated deletion preserves the original timestamp and actor. Actor may be unknown.
     */
    public void markDeleted(Instant at, String actor) {
        Objects.requireNonNull(at, "Deletion time is required");
        if (!isDeleted()) {
            deletedAt = at;
            deletedBy = actor;
        }
    }

    /**
     * Clears metadata only; the consumer must authorize and persist restoration.
     */
    public void restore() {
        deletedAt = null;
        deletedBy = null;
    }
}
