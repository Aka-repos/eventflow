package com.eventflow.modules.ticketing.application;

import com.eventflow.modules.ticketing.domain.DynamicQr;
import com.eventflow.modules.ticketing.domain.Ticket;
import com.eventflow.modules.ticketing.domain.event.QrEvents;
import com.eventflow.modules.ticketing.domain.exception.TicketBlockedException;
import com.eventflow.modules.ticketing.domain.port.DynamicQrRepository;
import com.eventflow.modules.ticketing.domain.port.QrSigner;
import com.eventflow.shared.config.PlatformConfig;
import com.eventflow.shared.outbox.OutboxPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;

/**
 * Emisión de QR (ADR-08). Regla central: **un solo QR vivo por boleto** — antes de emitir uno
 * nuevo se invalida el anterior. El índice único parcial de la BD respalda físicamente la regla.
 * El token firmado (ES256) se devuelve al llamador pero JAMÁS se persiste ni se publica en eventos.
 */
@Component
public class QrIssuer {

    private static final int DEFAULT_QR_EXPIRATION_MINUTES = 60;

    private final DynamicQrRepository qrRepository;
    private final QrSigner signer;
    private final OutboxPublisher outbox;
    private final PlatformConfig config;
    private final Clock clock;

    public QrIssuer(DynamicQrRepository qrRepository, QrSigner signer, OutboxPublisher outbox,
                    PlatformConfig config, Clock clock) {
        this.qrRepository = qrRepository;
        this.signer = signer;
        this.outbox = outbox;
        this.config = config;
        this.clock = clock;
    }

    /**
     * Devuelve un QR presentable para el boleto: reutiliza el vivo si aún no toca refrescar,
     * o invalida el anterior y emite uno nuevo. El boleto debe estar ACTIVE.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public IssuedQr issueOrReuse(Ticket ticket, int qrExpirationMinutes) {
        if (!ticket.isCheckInEligible()) {
            throw new TicketBlockedException("El boleto no admite QR (estado: " + ticket.getStatus() + ")");
        }
        Instant now = clock.instant();
        DynamicQr live = qrRepository.findLiveByTicketId(ticket.getId()).orElse(null);
        if (live != null && live.isValidAt(now) && now.isBefore(live.refreshAfter())) {
            return sign(live);
        }
        if (live != null) {
            live.invalidate();
            qrRepository.save(live);
        }
        return sign(emitFresh(ticket, qrExpirationMinutes, now));
    }

    /** Reembolso solicitado / publicación en Exchange: bloquea el QR vivo (reversible). */
    @Transactional(propagation = Propagation.MANDATORY)
    public void blockLive(Ticket ticket) {
        qrRepository.findLiveByTicketId(ticket.getId()).ifPresent(live -> {
            if (!live.isBlocked()) {
                live.block();
                qrRepository.save(live);
            }
        });
    }

    /** Reembolso rechazado / listing cancelado: desbloquea el QR. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void unblockLive(Ticket ticket) {
        qrRepository.findLiveByTicketId(ticket.getId()).ifPresent(live -> {
            if (live.isBlocked()) {
                live.unblock();
                qrRepository.save(live);
            }
        });
    }

    /** Reemisión forzada (organizador o transferencia): invalida el vivo y crea uno nuevo. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void invalidateLive(Ticket ticket, String cause, java.util.UUID actorId) {
        qrRepository.findLiveByTicketId(ticket.getId()).ifPresent(live -> {
            live.invalidate();
            qrRepository.save(live);
            outbox.publish("Ticket", ticket.getId(), QrEvents.QR_INVALIDATED, QrEvents.VERSION, actorId,
                    Map.of("qrId", live.getId().toString(), "ticketId", ticket.getId().toString(),
                            "cause", cause));
        });
    }

    private DynamicQr emitFresh(Ticket ticket, int qrExpirationMinutes, Instant now) {
        int minutes = qrExpirationMinutes > 0 ? qrExpirationMinutes : resolveExpiration(ticket);
        DynamicQr qr = qrRepository.save(
                DynamicQr.issueForTicket(ticket.getId(), signer.currentKeyId(), now, minutes));
        outbox.publish("Ticket", ticket.getId(), QrEvents.QR_GENERATED, QrEvents.VERSION,
                ticket.getCurrentOwnerId(), Map.of(
                        "qrId", qr.getId().toString(),
                        "ticketId", ticket.getId().toString(),
                        "subjectType", "TICKET"));
        return qr;
    }

    private int resolveExpiration(Ticket ticket) {
        Object minutes = ticket.getPolicySnapshot().get("qrExpirationMinutes");
        return minutes instanceof Number n ? n.intValue() : DEFAULT_QR_EXPIRATION_MINUTES;
    }

    private IssuedQr sign(DynamicQr qr) {
        return new IssuedQr(signer.sign(qr.getId(), qr.getExpiresAt()), qr.getExpiresAt(), qr.refreshAfter());
    }

    public record IssuedQr(String token, Instant expiresAt, Instant refreshAfter) {
    }
}
