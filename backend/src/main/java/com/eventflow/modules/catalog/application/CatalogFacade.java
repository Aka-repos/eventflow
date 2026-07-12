package com.eventflow.modules.catalog.application;

import com.eventflow.modules.catalog.domain.Category;
import com.eventflow.modules.catalog.domain.Event;
import com.eventflow.modules.catalog.domain.EventPolicy;
import com.eventflow.modules.catalog.domain.EventStatus;
import com.eventflow.modules.catalog.domain.exception.EventNotFoundException;
import com.eventflow.modules.catalog.domain.port.CategoryRepository;
import com.eventflow.modules.catalog.domain.port.EventPolicyRepository;
import com.eventflow.modules.catalog.domain.port.EventRepository;
import com.eventflow.modules.catalog.domain.port.EventZoneRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * ÚNICA superficie del catálogo visible para otros módulos (doc 10: ticketing S², ordering S).
 * Lanza EventNotFoundException (404 anti-enumeración) cuando corresponde.
 */
@Component
public class CatalogFacade {

    private final CatalogValidations validations;
    private final EventZoneRepository zoneRepository;
    private final EventRepository eventRepository;
    private final EventPolicyRepository policyRepository;
    private final CategoryRepository categoryRepository;

    public CatalogFacade(CatalogValidations validations, EventZoneRepository zoneRepository,
                         EventRepository eventRepository, EventPolicyRepository policyRepository,
                         CategoryRepository categoryRepository) {
        this.validations = validations;
        this.zoneRepository = zoneRepository;
        this.eventRepository = eventRepository;
        this.policyRepository = policyRepository;
        this.categoryRepository = categoryRepository;
    }

    @Transactional(readOnly = true)
    public void ensureEventOwnedBy(UUID eventId, UUID organizerId) {
        Event event = validations.requireOwnedEvent(eventId, organizerId);
        // la existencia del agregado basta; el estado lo gobiernan las reglas del llamador
        event.getId();
    }

    /** Nombre de la zona si pertenece al evento (para validar zoneId de tarifas y componer respuestas). */
    @Transactional(readOnly = true)
    public Optional<String> zoneNameForEvent(UUID zoneId, UUID eventId) {
        return zoneRepository.findByIdAndEventId(zoneId, eventId).map(z -> z.getName());
    }

    /**
     * Snapshot de compra (ordering→catalog S): datos del evento + política vigente serializada
     * para congelarla en el boleto (ADR-03). 404 si el evento no existe o no está a la venta.
     */
    @Transactional(readOnly = true)
    public EventPurchaseSnapshot purchaseSnapshot(UUID eventId) {
        Event event = eventRepository.findById(eventId)
                .filter(e -> e.getStatus() != EventStatus.DRAFT)
                .orElseThrow(EventNotFoundException::new);
        EventPolicy policy = policyRepository.findByEventId(eventId)
                .orElseThrow(EventNotFoundException::new);
        Map<String, Object> snapshot = new HashMap<>();
        snapshot.put("eventStartsAt", event.getStartsAt().toString());
        snapshot.put("eventEndsAt", event.getEndsAt().toString());
        snapshot.put("refundPct", (int) policy.getRefundPct());
        if (policy.getRefundWindowEndsAt() != null) {
            snapshot.put("refundWindowEndsAt", policy.getRefundWindowEndsAt().toString());
        }
        snapshot.put("exchangeEnabled", policy.isExchangeEnabled());
        snapshot.put("exchangeDepreciationPct", (int) policy.getExchangeDepreciationPct());
        if (policy.getExchangeListingDeadline() != null) {
            snapshot.put("exchangeListingDeadline", policy.getExchangeListingDeadline().toString());
        }
        snapshot.put("waitlistEnabled", policy.isWaitlistEnabled());
        snapshot.put("qrVisibilityHoursBefore", policy.getQrVisibilityHoursBefore());
        snapshot.put("qrExpirationMinutes", policy.getQrExpirationMinutes());
        return new EventPurchaseSnapshot(event.getId(), event.getTitle(), event.getOrganizerId(),
                event.getStatus().name(), event.getStartsAt(), snapshot);
    }

    /** Títulos por evento en batch (H3: descripciones de OrderResponse sin N+1). */
    @Transactional(readOnly = true)
    public Map<UUID, String> eventTitles(java.util.Collection<UUID> eventIds) {
        Map<UUID, String> titles = new HashMap<>();
        for (UUID id : eventIds) {
            eventRepository.findById(id).ifPresent(e -> titles.put(id, e.getTitle()));
        }
        return titles;
    }

    /** Tarjeta pública del evento (ticketing S²: para componer TicketResponse.event). */
    @Transactional(readOnly = true)
    public Optional<EventCard> eventCard(UUID eventId) {
        return eventRepository.findById(eventId).map(event -> {
            Category category = categoryRepository.findById(event.getCategoryId()).orElse(null);
            return new EventCard(event.getId(), event.getTitle(), event.getVenueName(),
                    event.getStartsAt(), event.getEndsAt(), event.getTimezone(), event.getStatus().name(),
                    event.getCoverUrl(), event.getCategoryId(),
                    category == null ? "" : category.getName(), category == null ? null : category.getIcon());
        });
    }

    /** Resultado público de la fachada (doc 10 §4). */
    public record EventPurchaseSnapshot(UUID eventId, String title, UUID organizerId, String status,
                                        Instant startsAt, Map<String, Object> policySnapshot) {

        public boolean isOnSale() {
            return "PUBLISHED".equals(status) || "SOLD_OUT".equals(status);
        }
    }

    public record EventCard(UUID eventId, String title, String venueName, Instant startsAt, Instant endsAt,
                            String timezone, String status, String coverUrl, int categoryId,
                            String categoryName, String categoryIcon) {
    }
}
