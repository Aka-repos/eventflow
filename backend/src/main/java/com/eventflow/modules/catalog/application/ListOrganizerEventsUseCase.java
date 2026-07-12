package com.eventflow.modules.catalog.application;

import com.eventflow.modules.catalog.domain.EventStatus;
import com.eventflow.modules.catalog.domain.port.EventListItem;
import com.eventflow.modules.catalog.domain.port.EventRepository;
import com.eventflow.modules.catalog.domain.Event;
import com.eventflow.modules.catalog.domain.port.CategoryRepository;
import com.eventflow.modules.catalog.domain.Category;
import com.eventflow.shared.web.CursorPage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ListOrganizerEventsUseCase {

    private final EventRepository eventRepository;
    private final CategoryRepository categoryRepository;

    public ListOrganizerEventsUseCase(EventRepository eventRepository, CategoryRepository categoryRepository) {
        this.eventRepository = eventRepository;
        this.categoryRepository = categoryRepository;
    }

    @Transactional(readOnly = true)
    public CursorPage<EventListItem> execute(UUID organizerId, EventStatus status, String cursor, int limit) {
        CursorPage<Event> page = eventRepository.findByOrganizer(organizerId, status, cursor, limit);
        Map<Short, Category> categories = page.items().stream()
                .map(Event::getCategoryId).distinct()
                .map(id -> categoryRepository.findById(id).orElse(null))
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toMap(Category::getId, c -> c));
        List<EventListItem> items = page.items().stream().map(e -> {
            Category c = categories.get(e.getCategoryId());
            return new EventListItem(e.getId(), e.getTitle(), e.getVenueName(), e.getStartsAt(), e.getEndsAt(),
                    e.getTimezone(), e.getStatus(), e.getCoverUrl(), e.getCategoryId(),
                    c == null ? "" : c.getName(), c == null ? null : c.getIcon(), null, null);
        }).toList();
        return new CursorPage<>(items, page.nextCursor());
    }
}
