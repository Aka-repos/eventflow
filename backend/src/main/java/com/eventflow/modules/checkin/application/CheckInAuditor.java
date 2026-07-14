package com.eventflow.modules.checkin.application;

import com.eventflow.modules.checkin.domain.port.EventCheckInRepository;
import com.eventflow.modules.ticketing.domain.event.QrEvents;
import com.eventflow.shared.outbox.OutboxPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/**
 * Registro antifraude del rechazo en TRANSACCIÓN PROPIA (REQUIRES_NEW): el CheckInDeniedException
 * que el use case lanza a continuación revierte su transacción, pero el rastro del intento fallido
 * debe sobrevivir (mismo patrón que la revocación de familia de tokens y la expiración de órdenes).
 */
@Component
public class CheckInAuditor {

    private final EventCheckInRepository checkInRepository;
    private final OutboxPublisher outbox;

    public CheckInAuditor(EventCheckInRepository checkInRepository, OutboxPublisher outbox) {
        this.checkInRepository = checkInRepository;
        this.outbox = outbox;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordDenial(UUID ticketId, UUID ticketRealEventId, UUID scannedEventId, UUID qrId,
                             UUID scannerId, String denialCode, Map<String, Object> device) {
        // La FK compuesta (ticket_id, event_id) exige el evento REAL del boleto — no el escaneado.
        // Solo registramos si conocemos el boleto (qr existe); firma inválida no llega aquí.
        if (ticketId != null && ticketRealEventId != null) {
            checkInRepository.recordDenied(ticketId, ticketRealEventId, qrId, scannerId, denialCode, device);
        }
        outbox.publish("Ticket", qrId, QrEvents.CHECKIN_DENIED, QrEvents.VERSION, scannerId, Map.of(
                "scannedEventId", scannedEventId.toString(),
                "denialCode", denialCode,
                "scannedBy", scannerId.toString()));
    }
}
