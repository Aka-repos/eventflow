package com.eventflow.modules.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Favorito usuario↔evento (idempotente por PK compuesta; tabla ops.favorites). */
@Entity
@Table(name = "favorites", schema = "ops")
public class Favorite {

    @EmbeddedId
    private FavoriteId id;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Favorite() {
    }

    private Favorite(FavoriteId id) {
        this.id = id;
    }

    public static Favorite of(UUID userId, UUID eventId) {
        return new Favorite(new FavoriteId(userId, eventId));
    }

    public FavoriteId getId() {
        return id;
    }

    @Embeddable
    public static class FavoriteId implements Serializable {

        @Column(name = "user_id")
        private UUID userId;

        @Column(name = "event_id")
        private UUID eventId;

        protected FavoriteId() {
        }

        public FavoriteId(UUID userId, UUID eventId) {
            this.userId = userId;
            this.eventId = eventId;
        }

        public UUID userId() {
            return userId;
        }

        public UUID eventId() {
            return eventId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof FavoriteId that)) {
                return false;
            }
            return Objects.equals(userId, that.userId) && Objects.equals(eventId, that.eventId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(userId, eventId);
        }
    }
}
