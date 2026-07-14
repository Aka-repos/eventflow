package com.eventflow.modules.checkin.application;

import com.eventflow.modules.catalog.application.CatalogFacade;
import com.eventflow.modules.identity.application.IdentityFacade;
import com.eventflow.shared.error.SemanticValidationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Asigna staff de acceso a un evento (checkin→catalog S para autorizar, →identity S⁷ para persistir).
 * Solo el organizador dueño; idempotente por índice único.
 */
@Service
public class AssignStaffUseCase {

    private final CatalogFacade catalog;
    private final IdentityFacade identity;

    public AssignStaffUseCase(CatalogFacade catalog, IdentityFacade identity) {
        this.catalog = catalog;
        this.identity = identity;
    }

    @Transactional
    public UUID execute(UUID organizerId, UUID eventId, String userEmail, List<String> permissions) {
        catalog.ensureEventOwnedBy(eventId, organizerId); // 404 anti-enumeración
        UUID staffUserId = identity.userIdByEmail(userEmail)
                .orElseThrow(() -> new SemanticValidationException("userEmail",
                        "No existe un usuario con ese email"));
        identity.assignStaff(eventId, staffUserId, permissions, organizerId);
        return staffUserId;
    }
}
