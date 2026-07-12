package com.eventflow.modules.catalog.domain.port;

import com.eventflow.shared.web.CursorPage;

/** Lado de consulta del catálogo público (búsqueda con keyset cursor, api/07). */
public interface EventSearchPort {

    CursorPage<EventListItem> search(EventSearchQuery query);

    java.util.List<EventListItem> favoritesOf(java.util.UUID userId);
}
