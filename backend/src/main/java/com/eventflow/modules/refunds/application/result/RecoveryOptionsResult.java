package com.eventflow.modules.refunds.application.result;

import com.eventflow.modules.ticketing.domain.RecoveryPolicy;

import java.util.UUID;

/** Composición de RecoveryOptionsResponse (la UI solo muestra la opción devuelta, ADR-19). */
public record RecoveryOptionsResult(UUID ticketId, RecoveryPolicy.Recovery recovery) {
}
