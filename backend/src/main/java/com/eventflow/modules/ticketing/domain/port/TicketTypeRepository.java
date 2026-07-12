package com.eventflow.modules.ticketing.domain.port;

import com.eventflow.modules.ticketing.domain.TicketType;

import java.util.Optional;
import java.util.UUID;

public interface TicketTypeRepository {

    TicketType save(TicketType ticketType);

    Optional<TicketType> findById(UUID id);

    java.util.List<TicketType> findAllByIds(java.util.Collection<UUID> ids);

    Optional<TicketType> findByIdAndEventId(UUID id, UUID eventId);

    /** SELECT ... FOR UPDATE — serializa la reserva/liberación de cupo (S2, orden canónico de locks). */
    Optional<TicketType> findByIdForUpdate(UUID id);

    void delete(TicketType ticketType);
}
