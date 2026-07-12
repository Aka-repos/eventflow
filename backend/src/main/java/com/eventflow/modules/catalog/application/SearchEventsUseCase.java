package com.eventflow.modules.catalog.application;

import com.eventflow.modules.catalog.domain.port.EventListItem;
import com.eventflow.modules.catalog.domain.port.EventSearchPort;
import com.eventflow.modules.catalog.domain.port.EventSearchQuery;
import com.eventflow.shared.web.CursorPage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SearchEventsUseCase {

    private final EventSearchPort searchPort;

    public SearchEventsUseCase(EventSearchPort searchPort) {
        this.searchPort = searchPort;
    }

    @Transactional(readOnly = true)
    public CursorPage<EventListItem> execute(EventSearchQuery query) {
        return searchPort.search(query);
    }
}
