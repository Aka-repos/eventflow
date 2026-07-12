package com.eventflow.modules.catalog.application;

import com.eventflow.modules.catalog.application.result.EventDetailResult;
import com.eventflow.modules.catalog.domain.Category;
import com.eventflow.modules.catalog.domain.Event;
import com.eventflow.modules.catalog.domain.EventPolicy;
import com.eventflow.modules.catalog.domain.EventStatus;
import com.eventflow.modules.catalog.domain.port.CategoryRepository;
import com.eventflow.modules.catalog.domain.port.EventPolicyRepository;
import com.eventflow.modules.catalog.domain.port.EventZoneRepository;
import com.eventflow.modules.catalog.domain.port.FavoriteRepository;
import com.eventflow.modules.catalog.domain.port.SponsorRepository;
import com.eventflow.modules.catalog.domain.port.TariffsReadPort;
import com.eventflow.modules.identity.application.IdentityFacade;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Compone EventDetail: agregado + categoría + tarifas (proyección) + zonas + sponsors + política. */
@Component
public class EventDetailAssembler {

    private final CategoryRepository categoryRepository;
    private final EventPolicyRepository policyRepository;
    private final EventZoneRepository zoneRepository;
    private final SponsorRepository sponsorRepository;
    private final TariffsReadPort tariffs;
    private final FavoriteRepository favorites;
    private final IdentityFacade identity;

    public EventDetailAssembler(CategoryRepository categoryRepository, EventPolicyRepository policyRepository,
                                EventZoneRepository zoneRepository, SponsorRepository sponsorRepository,
                                TariffsReadPort tariffs, FavoriteRepository favorites, IdentityFacade identity) {
        this.categoryRepository = categoryRepository;
        this.policyRepository = policyRepository;
        this.zoneRepository = zoneRepository;
        this.sponsorRepository = sponsorRepository;
        this.tariffs = tariffs;
        this.favorites = favorites;
        this.identity = identity;
    }

    /** Vista del dueño (respuestas de organizer): sin isFavorite. */
    public EventDetailResult assembleForOwner(Event event) {
        return assemble(event, null);
    }

    public EventDetailResult assemble(Event event, UUID viewerId) {
        Category category = categoryRepository.findById(event.getCategoryId()).orElse(null);
        EventPolicy policy = policyRepository.findByEventId(event.getId()).orElse(null);
        String organizerName = identity.userDisplayName(event.getOrganizerId());
        Boolean isFavorite = viewerId == null ? null : favorites.exists(viewerId, event.getId());
        boolean waitlistOpen = event.getStatus() == EventStatus.SOLD_OUT
                && policy != null && policy.isWaitlistEnabled();
        return new EventDetailResult(event, category, organizerName, tariffs.findByEventId(event.getId()),
                zoneRepository.findByEventId(event.getId()), sponsorRepository.findByEventId(event.getId()),
                policy, isFavorite, waitlistOpen);
    }
}
