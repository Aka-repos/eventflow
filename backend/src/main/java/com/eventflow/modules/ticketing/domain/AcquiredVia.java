package com.eventflow.modules.ticketing.domain;

/** ADR-19: EXCHANGE jamás puede solicitar reembolso. */
public enum AcquiredVia {
    PRIMARY, EXCHANGE
}
