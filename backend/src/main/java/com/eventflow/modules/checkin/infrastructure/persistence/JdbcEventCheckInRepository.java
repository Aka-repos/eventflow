package com.eventflow.modules.checkin.infrastructure.persistence;

import com.eventflow.modules.checkin.domain.port.EventCheckInRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * Escritura append-only de ops.event_checkins (REVOKE UPDATE/DELETE en V8). SQL nativo por ser
 * BIGINT IDENTITY de alto volumen; device_info como JSONB. Solo registra QRs existentes (FK).
 */
@Component
class JdbcEventCheckInRepository implements EventCheckInRepository {

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    JdbcEventCheckInRepository(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    /** uq_checkins_granted: un boleto entra una sola vez — un segundo GRANTED viola el índice. */
    @Override
    public void recordGranted(UUID ticketId, UUID eventId, UUID qrId, UUID scannedBy,
                              Map<String, Object> device) {
        jdbc.update("""
                        INSERT INTO ops.event_checkins (ticket_id, event_id, qr_id, scanned_by, result, device_info)
                        VALUES (?, ?, ?, ?, 'GRANTED', ?::jsonb)
                        """, ticketId, eventId, qrId, scannedBy, toJson(device));
    }

    @Override
    public void recordDenied(UUID ticketId, UUID eventId, UUID qrId, UUID scannedBy, String denialReason,
                             Map<String, Object> device) {
        jdbc.update("""
                        INSERT INTO ops.event_checkins
                            (ticket_id, event_id, qr_id, scanned_by, result, denial_reason, device_info)
                        VALUES (?, ?, ?, ?, 'DENIED', ?, ?::jsonb)
                        """, ticketId, eventId, qrId, scannedBy, denialReason, toJson(device));
    }

    private String toJson(Map<String, Object> device) {
        if (device == null || device.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(device);
        } catch (JsonProcessingException e) {
            return null;
        }
    }
}
