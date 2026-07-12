package com.eventflow.modules.catalog.infrastructure.persistence;

import com.eventflow.modules.catalog.domain.port.FavoriteRepository;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Favoritos idempotentes por SQL nativo (ON CONFLICT DO NOTHING sobre la PK compuesta). */
@Component
class JpaFavoriteRepositoryAdapter implements FavoriteRepository {

    private final EntityManager entityManager;

    JpaFavoriteRepositoryAdapter(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public void add(UUID userId, UUID eventId) {
        entityManager.createNativeQuery(
                        "INSERT INTO ops.favorites (user_id, event_id) VALUES (:userId, :eventId) "
                                + "ON CONFLICT DO NOTHING")
                .setParameter("userId", userId)
                .setParameter("eventId", eventId)
                .executeUpdate();
    }

    @Override
    public void remove(UUID userId, UUID eventId) {
        entityManager.createNativeQuery(
                        "DELETE FROM ops.favorites WHERE user_id = :userId AND event_id = :eventId")
                .setParameter("userId", userId)
                .setParameter("eventId", eventId)
                .executeUpdate();
    }

    @Override
    public boolean exists(UUID userId, UUID eventId) {
        return !entityManager.createNativeQuery(
                        "SELECT 1 FROM ops.favorites WHERE user_id = :userId AND event_id = :eventId")
                .setParameter("userId", userId)
                .setParameter("eventId", eventId)
                .getResultList().isEmpty();
    }
}
