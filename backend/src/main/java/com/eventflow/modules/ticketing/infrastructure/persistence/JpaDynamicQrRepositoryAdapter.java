package com.eventflow.modules.ticketing.infrastructure.persistence;

import com.eventflow.modules.ticketing.domain.DynamicQr;
import com.eventflow.modules.ticketing.domain.exception.TicketBlockedException;
import com.eventflow.modules.ticketing.domain.port.DynamicQrRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
class JpaDynamicQrRepositoryAdapter implements DynamicQrRepository {

    private final SpringDataDynamicQrRepository jpa;

    JpaDynamicQrRepositoryAdapter(SpringDataDynamicQrRepository jpa) {
        this.jpa = jpa;
    }

    /** uq_dynamic_qrs_ticket_live: una carrera que intente dos QR vivos por boleto se traduce a conflicto. */
    @Override
    public DynamicQr save(DynamicQr qr) {
        try {
            return jpa.saveAndFlush(qr);
        } catch (DataIntegrityViolationException ex) {
            throw new TicketBlockedException("Ya existe un QR vivo para este boleto");
        }
    }

    @Override
    public Optional<DynamicQr> findById(UUID id) {
        return jpa.findById(id);
    }

    @Override
    public Optional<DynamicQr> findLiveByTicketId(UUID ticketId) {
        return jpa.findLiveByTicketId(ticketId);
    }

    @Override
    public Optional<DynamicQr> findByIdForUpdate(UUID id) {
        return jpa.lockById(id);
    }
}
