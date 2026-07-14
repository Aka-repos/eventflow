package com.eventflow.modules.checkin.domain.port;

import java.util.Map;
import java.util.UUID;

/**
 * Registro append-only de check-ins (ops.event_checkins; REVOKE UPDATE/DELETE en V8).
 * Solo se registran intentos contra QRs que EXISTEN (qr_id es FK NOT NULL): los tokens con
 * firma inválida se rechazan antes y quedan en la auditoría general, no aquí.
 */
public interface EventCheckInRepository {

    void recordGranted(UUID ticketId, UUID eventId, UUID qrId, UUID scannedBy, Map<String, Object> device);

    void recordDenied(UUID ticketId, UUID eventId, UUID qrId, UUID scannedBy, String denialReason,
                      Map<String, Object> device);
}
