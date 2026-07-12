package com.eventflow.modules.ledger.domain.port;

import com.eventflow.modules.ledger.domain.LedgerEntry;

/** Puerto de escritura append-only del ledger. */
public interface LedgerAppender {

    void append(LedgerEntry entry);
}
