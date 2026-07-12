package com.eventflow.modules.ledger.infrastructure.persistence;

import com.eventflow.modules.ledger.domain.LedgerEntry;
import com.eventflow.modules.ledger.domain.port.LedgerAppender;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;

/** Escritura append-only; jamás update/delete (REVOKE físico en V8). */
@Component
class LedgerEntryWriter implements LedgerAppender {

    private final EntityManager entityManager;

    LedgerEntryWriter(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public void append(LedgerEntry entry) {
        entityManager.persist(entry);
    }
}
