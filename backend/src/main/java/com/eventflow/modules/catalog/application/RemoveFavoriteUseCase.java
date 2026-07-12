package com.eventflow.modules.catalog.application;

import com.eventflow.modules.catalog.domain.port.FavoriteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class RemoveFavoriteUseCase {

    private final FavoriteRepository favoriteRepository;

    public RemoveFavoriteUseCase(FavoriteRepository favoriteRepository) {
        this.favoriteRepository = favoriteRepository;
    }

    /** Idempotente: quitar algo que no está también es 204 (contrato). */
    @Transactional
    public void execute(UUID userId, UUID eventId) {
        favoriteRepository.remove(userId, eventId);
    }
}
