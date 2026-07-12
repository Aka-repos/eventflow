package com.eventflow.modules.catalog.application;

import com.eventflow.modules.catalog.application.command.UpdatePolicyCommand;
import com.eventflow.modules.catalog.domain.EventPolicy;
import com.eventflow.modules.catalog.domain.exception.EventNotFoundException;
import com.eventflow.modules.catalog.domain.port.EventPolicyRepository;
import com.eventflow.shared.error.VersionConflictException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UpdateEventPolicyUseCase {

    private final EventPolicyRepository policyRepository;
    private final CatalogValidations validations;

    public UpdateEventPolicyUseCase(EventPolicyRepository policyRepository, CatalogValidations validations) {
        this.policyRepository = policyRepository;
        this.validations = validations;
    }

    /** Reemplazo completo con If-Match. ADR-03: no afecta boletos ya vendidos (snapshot). */
    @Transactional
    public EventPolicy execute(UpdatePolicyCommand cmd) {
        validations.requireOwnedEvent(cmd.eventId(), cmd.organizerId());
        EventPolicy policy = policyRepository.findByEventId(cmd.eventId())
                .orElseThrow(EventNotFoundException::new);
        if (policy.getVersion() != cmd.ifMatchVersion()) {
            throw new VersionConflictException(policy.getVersion());
        }
        policy.replace(cmd.refundWindowEndsAt(), cmd.exchangeEnabled(), (short) cmd.exchangeDepreciationPct(),
                cmd.exchangeListingDeadline(), cmd.waitlistEnabled(), cmd.waitlistOfferMinutes(),
                cmd.tempReservationMinutes(), cmd.qrVisibilityHoursBefore(), cmd.qrExpirationMinutes(),
                cmd.cancellationPolicy(), cmd.extraPolicies());
        return policyRepository.save(policy);
    }
}
