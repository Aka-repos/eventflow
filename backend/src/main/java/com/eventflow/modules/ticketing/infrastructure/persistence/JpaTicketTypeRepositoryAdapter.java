package com.eventflow.modules.ticketing.infrastructure.persistence;

import com.eventflow.modules.ticketing.domain.TicketType;
import com.eventflow.modules.ticketing.domain.port.TicketTypeRepository;
import com.eventflow.shared.error.SemanticValidationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
class JpaTicketTypeRepositoryAdapter implements TicketTypeRepository {

    private final SpringDataTicketTypeRepository jpa;

    JpaTicketTypeRepositoryAdapter(SpringDataTicketTypeRepository jpa) {
        this.jpa = jpa;
    }

    /** El unique (event_id, name) perdido en carrera se traduce a 422 (contrato createTicketType). */
    @Override
    public TicketType save(TicketType ticketType) {
        try {
            return jpa.saveAndFlush(ticketType);
        } catch (DataIntegrityViolationException ex) {
            throw new SemanticValidationException("name", "Ya existe una tarifa con ese nombre en el evento");
        }
    }

    @Override
    public Optional<TicketType> findById(UUID id) {
        return jpa.findById(id);
    }

    @Override
    public java.util.List<TicketType> findAllByIds(java.util.Collection<UUID> ids) {
        return ids.isEmpty() ? java.util.List.of() : jpa.findAllById(ids);
    }

    @Override
    public Optional<TicketType> findByIdAndEventId(UUID id, UUID eventId) {
        return jpa.findByIdAndEventId(id, eventId);
    }

    @Override
    public Optional<TicketType> findByIdForUpdate(UUID id) {
        return jpa.lockById(id);
    }

    @Override
    public void delete(TicketType ticketType) {
        jpa.delete(ticketType);
        jpa.flush();
    }
}
