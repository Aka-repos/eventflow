package com.eventflow.modules.catalog.application;

import com.eventflow.modules.catalog.domain.port.EventListItem;
import com.eventflow.modules.catalog.domain.port.EventSearchPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class ListFavoritesUseCase {

    private final EventSearchPort searchPort;

    public ListFavoritesUseCase(EventSearchPort searchPort) {
        this.searchPort = searchPort;
    }

    @Transactional(readOnly = true)
    public List<EventListItem> execute(UUID userId) {
        return searchPort.favoritesOf(userId);
    }
}
