package com.eventflow.modules.checkin.application;

import com.eventflow.modules.catalog.application.CatalogFacade;
import com.eventflow.modules.identity.application.IdentityFacade;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

/** Revoca staff de un evento (idempotente). */
@Service
public class RemoveStaffUseCase {

    private final CatalogFacade catalog;
    private final IdentityFacade identity;
    private final Clock clock;

    public RemoveStaffUseCase(CatalogFacade catalog, IdentityFacade identity, Clock clock) {
        this.catalog = catalog;
        this.identity = identity;
        this.clock = clock;
    }

    @Transactional
    public void execute(UUID organizerId, UUID eventId, UUID staffUserId) {
        catalog.ensureEventOwnedBy(eventId, organizerId);
        identity.revokeStaff(eventId, staffUserId, clock.instant());
    }
}
