package com.eventflow.modules.checkin.application;

import com.eventflow.modules.catalog.application.CatalogFacade;
import com.eventflow.modules.checkin.application.EventCheckInUseCase.CheckInDeniedException;
import com.eventflow.modules.checkin.domain.port.EventCheckInRepository;
import com.eventflow.modules.identity.application.IdentityFacade;
import com.eventflow.modules.ticketing.application.TicketingFacade;
import com.eventflow.modules.ticketing.domain.port.QrSigner;
import com.eventflow.shared.outbox.OutboxPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** El escáner nunca decide: toda validación es server-side. Aquí, con puertos simulados. */
@ExtendWith(MockitoExtension.class)
class EventCheckInUseCaseTest {

    private static final Instant NOW = Instant.parse("2027-02-01T20:00:00Z");

    @Mock QrSigner signer;
    @Mock TicketingFacade ticketing;
    @Mock CatalogFacade catalog;
    @Mock IdentityFacade identity;
    @Mock EventCheckInRepository checkInRepository;
    @Mock CheckInAuditor auditor;
    @Mock OutboxPublisher outbox;

    private EventCheckInUseCase useCase;
    private final UUID scannerId = UUID.randomUUID();
    private final UUID eventId = UUID.randomUUID();
    private final UUID qrId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        useCase = new EventCheckInUseCase(signer, ticketing, catalog, identity, checkInRepository,
                auditor, outbox, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private void authorizedOrganizer() {
        when(catalog.isEventOrganizer(eventId, scannerId)).thenReturn(true);
    }

    @Test
    void unauthorized_scanner_never_touches_the_qr() {
        when(catalog.isEventOrganizer(eventId, scannerId)).thenReturn(false);
        when(identity.isActiveStaff(eventId, scannerId)).thenReturn(false);

        assertThatThrownBy(() -> useCase.execute(scannerId, eventId, "tok", Map.of()))
                .isInstanceOf(CheckInDeniedException.class)
                .satisfies(t -> assertThat(((CheckInDeniedException) t).errorCode().code())
                        .isEqualTo("staff_not_assigned"));
        verify(signer, never()).verify(any());
    }

    @Test
    void expired_token_maps_to_qr_expired() {
        authorizedOrganizer();
        when(signer.verify("tok")).thenReturn(new QrSigner.Verification.Expired());

        assertThatThrownBy(() -> useCase.execute(scannerId, eventId, "tok", Map.of()))
                .satisfies(t -> assertThat(((CheckInDeniedException) t).errorCode().code())
                        .isEqualTo("qr_expired"));
        verify(ticketing, never()).resolveAndConsume(any(), any());
    }

    @Test
    void invalid_token_maps_to_qr_invalid() {
        authorizedOrganizer();
        when(signer.verify("tok")).thenReturn(new QrSigner.Verification.Invalid("bad"));

        assertThatThrownBy(() -> useCase.execute(scannerId, eventId, "tok", Map.of()))
                .satisfies(t -> assertThat(((CheckInDeniedException) t).errorCode().code())
                        .isEqualTo("qr_invalid"));
    }

    @Test
    void denied_resolution_records_via_auditor_then_throws() {
        authorizedOrganizer();
        when(signer.verify("tok")).thenReturn(new QrSigner.Verification.Valid(qrId, "kid"));
        UUID ticketId = UUID.randomUUID();
        when(ticketing.resolveAndConsume(qrId, eventId)).thenReturn(
                new TicketingFacade.CheckInResolution(false, "already_used", ticketId, eventId,
                        UUID.randomUUID(), qrId, UUID.randomUUID()));

        assertThatThrownBy(() -> useCase.execute(scannerId, eventId, "tok", Map.of()))
                .satisfies(t -> assertThat(((CheckInDeniedException) t).errorCode().code())
                        .isEqualTo("already_used"));
        verify(auditor).recordDenial(eq(ticketId), eq(eventId), eq(eventId), eq(qrId), eq(scannerId),
                eq("already_used"), any());
    }

    @Test
    void granted_resolution_records_and_composes_response() {
        authorizedOrganizer();
        when(signer.verify("tok")).thenReturn(new QrSigner.Verification.Valid(qrId, "kid"));
        UUID ticketId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID tariffId = UUID.randomUUID();
        when(ticketing.resolveAndConsume(qrId, eventId)).thenReturn(
                new TicketingFacade.CheckInResolution(true, null, ticketId, eventId, ownerId, qrId, tariffId));
        when(identity.userDisplayName(ownerId)).thenReturn("Ana Asistente");
        when(ticketing.ticketTypeName(tariffId)).thenReturn("VIP");
        lenient().when(ticketing.zoneNameForTicketType(tariffId, eventId)).thenReturn("Palco");

        var result = useCase.execute(scannerId, eventId, "tok", Map.of());

        assertThat(result.attendeeName()).isEqualTo("Ana Asistente");
        assertThat(result.ticketTypeName()).isEqualTo("VIP");
        verify(checkInRepository).recordGranted(eq(ticketId), eq(eventId), eq(qrId), eq(scannerId), any());
        verify(outbox).publish(eq("Ticket"), eq(ticketId), eq("CheckInCompleted"), eq(1), eq(scannerId), any());
    }

    // helper para eq(...) legible
    private static <T> T eq(T value) {
        return org.mockito.ArgumentMatchers.eq(value);
    }
}
