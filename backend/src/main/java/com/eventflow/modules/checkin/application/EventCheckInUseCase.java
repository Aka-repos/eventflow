package com.eventflow.modules.checkin.application;

import com.eventflow.modules.catalog.application.CatalogFacade;
import com.eventflow.modules.checkin.application.result.CheckInResultView;
import com.eventflow.modules.checkin.domain.port.EventCheckInRepository;
import com.eventflow.modules.identity.application.IdentityFacade;
import com.eventflow.modules.ticketing.application.TicketingFacade;
import com.eventflow.modules.ticketing.domain.event.QrEvents;
import com.eventflow.modules.ticketing.domain.port.QrSigner;
import com.eventflow.shared.error.DomainException;
import com.eventflow.shared.error.ErrorCode;
import com.eventflow.shared.outbox.OutboxPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Map;
import java.util.UUID;

/**
 * Check-in de evento (S7): verifica firma JWS ES256 → autoriza al escáner (organizador o staff) →
 * bajo lock del QR valida y consuma atómicamente → registra (GRANTED/DENIED) y publica el evento.
 * Toda la validación es server-side; el cliente jamás decide si un QR es válido.
 */
@Service
public class EventCheckInUseCase {

    private final QrSigner signer;
    private final TicketingFacade ticketing;
    private final CatalogFacade catalog;
    private final IdentityFacade identity;
    private final EventCheckInRepository checkInRepository;
    private final CheckInAuditor auditor;
    private final OutboxPublisher outbox;
    private final Clock clock;

    public EventCheckInUseCase(QrSigner signer, TicketingFacade ticketing, CatalogFacade catalog,
                               IdentityFacade identity, EventCheckInRepository checkInRepository,
                               CheckInAuditor auditor, OutboxPublisher outbox, Clock clock) {
        this.signer = signer;
        this.ticketing = ticketing;
        this.catalog = catalog;
        this.identity = identity;
        this.checkInRepository = checkInRepository;
        this.auditor = auditor;
        this.outbox = outbox;
        this.clock = clock;
    }

    @Transactional
    public CheckInResultView execute(UUID scannerId, UUID eventId, String qrToken, Map<String, Object> device) {
        // 1. autorización del escáner (organizador dueño del evento o staff activo)
        boolean isOrganizer = catalog.isEventOrganizer(eventId, scannerId);
        if (!isOrganizer && !identity.isActiveStaff(eventId, scannerId)) {
            // 403 antes de tocar el QR: no revelamos nada del evento a un no autorizado
            throw new CheckInDeniedException(ErrorCode.STAFF_NOT_ASSIGNED,
                    "No estás autorizado para escanear este evento");
        }

        // 2. firma + expiración del token (fraude/manipulación/caducidad) — antes de la BD
        QrSigner.Verification verification = signer.verify(qrToken);
        if (verification instanceof QrSigner.Verification.Expired) {
            throw new CheckInDeniedException(ErrorCode.QR_EXPIRED, "El QR expiró");
        }
        if (!(verification instanceof QrSigner.Verification.Valid valid)) {
            throw new CheckInDeniedException(ErrorCode.QR_INVALID, "El QR no es válido");
        }
        UUID qrId = valid.qrId();

        // 3. resolución atómica bajo lock del QR (estado, evento, propiedad, consumo)
        TicketingFacade.CheckInResolution resolution = ticketing.resolveAndConsume(qrId, eventId);

        if (!resolution.granted()) {
            // registro antifraude en TX propia (sobrevive al rollback de la excepción de rechazo)
            auditor.recordDenial(resolution.ticketId(), resolution.eventId(), eventId,
                    resolution.qrId() == null ? qrId : resolution.qrId(), scannerId,
                    resolution.denialCode(), device);
            throw new CheckInDeniedException(ErrorCode.valueOf(codeToEnum(resolution.denialCode())),
                    denialMessage(resolution.denialCode()));
        }

        // 4. GRANTED: registrar + publicar + componer respuesta
        checkInRepository.recordGranted(resolution.ticketId(), eventId, resolution.qrId(), scannerId, device);
        outbox.publish("Ticket", resolution.ticketId(), QrEvents.CHECKIN_COMPLETED, QrEvents.VERSION,
                scannerId, Map.of(
                        "ticketId", resolution.ticketId().toString(),
                        "eventId", eventId.toString(),
                        "scannedBy", scannerId.toString(),
                        "result", "GRANTED"));
        return new CheckInResultView(
                identity.userDisplayName(resolution.ownerId()),
                ticketing.ticketTypeName(resolution.ticketTypeId()),
                ticketing.zoneNameForTicketType(resolution.ticketTypeId(), eventId),
                clock.instant());
    }

    private static String codeToEnum(String code) {
        return code.toUpperCase(java.util.Locale.ROOT);
    }

    private static String denialMessage(String code) {
        return switch (code) {
            case "already_used" -> "El boleto ya fue usado";
            case "checkin_wrong_event" -> "El QR pertenece a otro evento";
            case "ticket_blocked" -> "El boleto no está activo";
            default -> "El QR no es válido";
        };
    }

    /** Rechazo de check-in que se traduce a Problem RFC 9457 con su código exacto. */
    public static class CheckInDeniedException extends DomainException {

        public CheckInDeniedException(ErrorCode code, String detail) {
            super(code, detail);
        }
    }
}
