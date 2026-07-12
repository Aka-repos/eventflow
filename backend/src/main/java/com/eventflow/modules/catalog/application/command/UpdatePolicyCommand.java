package com.eventflow.modules.catalog.application.command;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record UpdatePolicyCommand(UUID organizerId, UUID eventId, int ifMatchVersion,
                                  Instant refundWindowEndsAt, boolean exchangeEnabled,
                                  int exchangeDepreciationPct, Instant exchangeListingDeadline,
                                  boolean waitlistEnabled, int waitlistOfferMinutes,
                                  int tempReservationMinutes, int qrVisibilityHoursBefore,
                                  int qrExpirationMinutes, String cancellationPolicy,
                                  Map<String, Object> extraPolicies) {
}
