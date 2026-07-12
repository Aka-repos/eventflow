package com.eventflow.modules.catalog.application;

import com.eventflow.modules.catalog.domain.exception.EventNotFoundException;
import com.eventflow.modules.catalog.domain.port.EventRepository;
import com.eventflow.modules.catalog.domain.port.FavoriteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class AddFavoriteUseCase {

    private final FavoriteRepository favoriteRepository;
    private final EventRepository eventRepository;

    public AddFavoriteUseCase(FavoriteRepository favoriteRepository, EventRepository eventRepository) {
        this.favoriteRepository = favoriteRepository;
        this.eventRepository = eventRepository;
    }

    /** Idempotente; 404 si el evento no existe (contrato). */
    @Transactional
    public void execute(UUID userId, UUID eventId) {
        if (!eventRepository.existsById(eventId)) {
            throw new EventNotFoundException();
        }
        favoriteRepository.add(userId, eventId);
    }
}
