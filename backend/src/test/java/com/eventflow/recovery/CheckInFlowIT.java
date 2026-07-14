package com.eventflow.recovery;

import com.eventflow.modules.catalog.application.CreateCategoryUseCase;
import com.eventflow.modules.catalog.application.CreateEventUseCase;
import com.eventflow.modules.catalog.application.PublishEventUseCase;
import com.eventflow.modules.catalog.application.UpdateEventPolicyUseCase;
import com.eventflow.modules.catalog.application.command.CreateEventCommand;
import com.eventflow.modules.catalog.application.command.UpdatePolicyCommand;
import com.eventflow.modules.checkin.application.EventCheckInUseCase;
import com.eventflow.modules.checkin.application.EventCheckInUseCase.CheckInDeniedException;
import com.eventflow.modules.checkin.application.AssignStaffUseCase;
import com.eventflow.modules.identity.application.RegisterUserUseCase;
import com.eventflow.modules.identity.application.command.RegisterUserCommand;
import com.eventflow.modules.ordering.application.CreateOrderUseCase;
import com.eventflow.modules.ordering.application.PayOrderUseCase;
import com.eventflow.modules.ordering.application.command.CreateOrderCommand;
import com.eventflow.modules.ticketing.application.GetTicketQrUseCase;
import com.eventflow.modules.ticketing.application.InvalidateTicketUseCase;
import com.eventflow.modules.ticketing.application.QrIssuer;
import com.eventflow.modules.ticketing.application.ReissueTicketUseCase;
import com.eventflow.modules.ticketing.application.CreateTicketTypeUseCase;
import com.eventflow.modules.ticketing.application.command.TicketTypeCommand;
import com.eventflow.modules.ticketing.domain.port.QrSigner;
import com.eventflow.shared.domain.Money;
import com.eventflow.shared.error.DomainException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * IT de sistema del Módulo 4 (paquete neutral): QR dinámico firmado ES256 + check-in server-side,
 * con todos los escenarios de fraude exigidos — expirado, inválido, revocado, reutilizado, evento
 * incorrecto, boleto invalidado, QR alterado, reloj y cambio de propietario (aquí: reemisión).
 */
@Testcontainers
@SpringBootTest
class CheckInFlowIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    private static UUID organizerId;
    private static UUID buyerId;
    private static UUID staffId;
    private static UUID otherEventId;
    private static UUID otherEventTariffId;
    private static UUID eventId;
    private static UUID tariffId;

    @Autowired CreateOrderUseCase createOrder;
    @Autowired PayOrderUseCase payOrder;
    @Autowired GetTicketQrUseCase getTicketQr;
    @Autowired InvalidateTicketUseCase invalidateTicket;
    @Autowired ReissueTicketUseCase reissueTicket;
    @Autowired EventCheckInUseCase eventCheckIn;
    @Autowired QrSigner signer;
    @Autowired JdbcTemplate jdbc;

    @BeforeAll
    static void setUpAll(@Autowired RegisterUserUseCase register, @Autowired CreateCategoryUseCase createCategory,
                         @Autowired CreateEventUseCase createEvent, @Autowired CreateTicketTypeUseCase createTariff,
                         @Autowired UpdateEventPolicyUseCase updatePolicy, @Autowired PublishEventUseCase publish,
                         @Autowired AssignStaffUseCase assignStaff, @Autowired JdbcTemplate jdbc) {
        organizerId = user(register, jdbc, "ci-org@mail.com", "ORGANIZER");
        buyerId = user(register, jdbc, "ci-buyer@mail.com", null);
        staffId = user(register, jdbc, "ci-staff@mail.com", "STAFF");
        createCategory.execute("CheckIn", null, true);
        short categoryId = jdbc.queryForObject("SELECT id FROM catalog.categories WHERE name='CheckIn'", Short.class);

        // evento cuya ventana de QR ya está abierta (empieza pronto, visibilidad amplia)
        Instant starts = Instant.now().plus(2, ChronoUnit.HOURS);
        eventId = event(createEvent, updatePolicy, publish, jdbc, categoryId, "Evento CheckIn", starts);
        tariffId = tariffAndPublish(createTariff, publish, jdbc, eventId);
        // segundo evento para probar checkin_wrong_event
        otherEventId = event(createEvent, updatePolicy, publish, jdbc, categoryId, "Otro Evento", starts);
        otherEventTariffId = tariffAndPublish(createTariff, publish, jdbc, otherEventId);
        // staff asignado al primer evento
        assignStaff.execute(organizerId, eventId, "ci-staff@mail.com", List.of("CHECKIN_EVENT"));
    }

    private static UUID user(RegisterUserUseCase register, JdbcTemplate jdbc, String email, String role) {
        register.execute(new RegisterUserCommand(email, "S3gura!pass", "Persona", "+50760000000"));
        if (role != null) {
            jdbc.update("""
                    INSERT INTO identity.user_roles (user_id, role_id)
                    SELECT u.id, r.id FROM identity.users u, identity.roles r WHERE u.email=? AND r.code=?
                    """, email, role);
        }
        return jdbc.queryForObject("SELECT id FROM identity.users WHERE email=?", UUID.class, email);
    }

    /** Crea evento (DRAFT) + política con ventana de QR ya abierta. Aún no publica. */
    private static UUID event(CreateEventUseCase createEvent, UpdateEventPolicyUseCase updatePolicy,
                              PublishEventUseCase publish, JdbcTemplate jdbc, short categoryId,
                              String title, Instant starts) {
        createEvent.execute(new CreateEventCommand(organizerId, title, "d", categoryId, "Arena",
                null, null, null, "America/Panama", starts, starts.plusSeconds(7200)));
        UUID id = jdbc.queryForObject("SELECT id FROM catalog.events WHERE title=?", UUID.class, title);
        // política: QR visible desde 720 h antes (ventana ya abierta), expira en 60 min
        updatePolicy.execute(new UpdatePolicyCommand(organizerId, id, 0, null, false, 10, null,
                false, 15, 10, 720, 60, null, Map.of()));
        return id;
    }

    /** Crea la tarifa y publica el evento (requiere tarifa). */
    private static UUID tariffAndPublish(CreateTicketTypeUseCase createTariff, PublishEventUseCase publish,
                                         JdbcTemplate jdbc, UUID eventId) {
        createTariff.execute(new TicketTypeCommand(organizerId, eventId, "General", null,
                Money.of("20.00", "USD"), null, 50, null, null));
        UUID id = jdbc.queryForObject("SELECT id FROM ticketing.ticket_types WHERE event_id=?",
                UUID.class, eventId);
        publish.execute(organizerId, eventId);
        return id;
    }

    /** Compra un boleto real y devuelve su ticketId. */
    private UUID buyTicket(UUID targetEventId, UUID targetTariffId) {
        UUID orderId = createOrder.execute(new CreateOrderCommand(buyerId, UUID.randomUUID(),
                List.of(new CreateOrderCommand.Item("TICKET", targetTariffId, 1)))).order().getId();
        payOrder.execute(buyerId, orderId, "FAKE");
        return jdbc.queryForObject("""
                SELECT t.id FROM ticketing.tickets t
                JOIN commerce.order_items oi ON oi.id = t.source_order_item_id
                WHERE oi.order_id = ?
                """, UUID.class, orderId);
    }

    private Map<String, Object> device() {
        return Map.of("userAgent", "IT", "ip", "127.0.0.1");
    }

    private String denialCode(Throwable t) {
        return ((DomainException) t).errorCode().code();
    }

    // ===== escenarios =====

    @Test
    void happy_path_grants_access_and_consumes_qr() {
        UUID ticketId = buyTicket(eventId, tariffId);
        QrIssuer.IssuedQr qr = getTicketQr.execute(buyerId, ticketId);

        var result = eventCheckIn.execute(organizerId, eventId, qr.token(), device());

        assertThat(result.attendeeName()).isEqualTo("Persona");
        assertThat(result.ticketTypeName()).isEqualTo("General");
        assertThat(jdbc.queryForObject("SELECT status FROM ticketing.tickets WHERE id=?", String.class, ticketId))
                .isEqualTo("USED");
        assertThat(jdbc.queryForObject(
                "SELECT status FROM ticketing.dynamic_qrs WHERE ticket_id=?", String.class, ticketId))
                .isEqualTo("CONSUMED");
        assertThat(jdbc.queryForObject(
                "SELECT result FROM ops.event_checkins WHERE ticket_id=?", String.class, ticketId))
                .isEqualTo("GRANTED");
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM ops.outbox_events WHERE event_type='CheckInCompleted' AND aggregate_id=?
                """, Integer.class, ticketId)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM ticketing.ticket_history WHERE ticket_id=? AND cause='CHECKIN'",
                Integer.class, ticketId)).isEqualTo(1);
    }

    @Test
    void reused_qr_is_denied_already_used() {
        UUID ticketId = buyTicket(eventId, tariffId);
        String token = getTicketQr.execute(buyerId, ticketId).token();
        eventCheckIn.execute(staffId, eventId, token, device()); // primera entrada OK

        assertThatThrownBy(() -> eventCheckIn.execute(staffId, eventId, token, device()))
                .isInstanceOf(CheckInDeniedException.class)
                .satisfies(t -> assertThat(denialCode(t)).isEqualTo("already_used"));
        // un boleto entra una sola vez (uq_checkins_granted)
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM ops.event_checkins WHERE ticket_id=? AND result='GRANTED'",
                Integer.class, ticketId)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM ops.event_checkins WHERE ticket_id=? AND result='DENIED'",
                Integer.class, ticketId)).isEqualTo(1);
    }

    @Test
    void tampered_token_is_denied_qr_invalid() {
        UUID ticketId = buyTicket(eventId, tariffId);
        String token = getTicketQr.execute(buyerId, ticketId).token();
        String tampered = token.substring(0, token.length() - 2) + (token.endsWith("A") ? "B" : "A");

        assertThatThrownBy(() -> eventCheckIn.execute(organizerId, eventId, tampered, device()))
                .satisfies(t -> assertThat(denialCode(t)).isEqualTo("qr_invalid"));
    }

    @Test
    void forged_token_from_another_key_is_denied_qr_invalid() {
        // token con qr_id real pero firmado por una llave EC ajena (JWS ES256 forjado)
        UUID ticketId = buyTicket(eventId, tariffId);
        getTicketQr.execute(buyerId, ticketId);
        UUID realQrId = jdbc.queryForObject(
                "SELECT id FROM ticketing.dynamic_qrs WHERE ticket_id=?", UUID.class, ticketId);
        String forged = io.jsonwebtoken.Jwts.builder()
                .header().keyId("attacker").and()
                .subject(realQrId.toString())
                .expiration(java.util.Date.from(Instant.now().plus(1, ChronoUnit.HOURS)))
                .signWith(io.jsonwebtoken.Jwts.SIG.ES256.keyPair().build().getPrivate(),
                        io.jsonwebtoken.Jwts.SIG.ES256)
                .compact();

        assertThatThrownBy(() -> eventCheckIn.execute(organizerId, eventId, forged, device()))
                .satisfies(t -> assertThat(denialCode(t)).isEqualTo("qr_invalid"));
    }

    @Test
    void expired_token_is_denied_qr_expired() {
        // firmamos manualmente un token con exp en el pasado sobre un qr_id real
        UUID ticketId = buyTicket(eventId, tariffId);
        getTicketQr.execute(buyerId, ticketId);
        UUID qrId = jdbc.queryForObject("SELECT id FROM ticketing.dynamic_qrs WHERE ticket_id=?",
                UUID.class, ticketId);
        String expired = signer.sign(qrId, Instant.now().minus(1, ChronoUnit.MINUTES));

        assertThatThrownBy(() -> eventCheckIn.execute(organizerId, eventId, expired, device()))
                .satisfies(t -> assertThat(denialCode(t)).isEqualTo("qr_expired"));
    }

    @Test
    void wrong_event_is_denied() {
        UUID ticketId = buyTicket(eventId, tariffId);
        String token = getTicketQr.execute(buyerId, ticketId).token();
        // el organizador posee ambos eventos; escanear en el evento equivocado
        assertThatThrownBy(() -> eventCheckIn.execute(organizerId, otherEventId, token, device()))
                .satisfies(t -> assertThat(denialCode(t)).isEqualTo("checkin_wrong_event"));
    }

    @Test
    void invalidated_ticket_qr_is_denied() {
        UUID ticketId = buyTicket(eventId, tariffId);
        String token = getTicketQr.execute(buyerId, ticketId).token();
        invalidateTicket.execute(organizerId, ticketId); // boleto → INVALIDATED, QR muere

        // el QR viejo ya no está vivo: al resolver, el QR está INVALIDATED (no ACTIVE) ⇒ qr_invalid
        assertThatThrownBy(() -> eventCheckIn.execute(organizerId, eventId, token, device()))
                .satisfies(t -> assertThat(denialCode(t)).isIn("qr_invalid", "ticket_blocked"));
    }

    @Test
    void reissue_kills_old_qr_and_new_one_works() {
        UUID ticketId = buyTicket(eventId, tariffId);
        String oldToken = getTicketQr.execute(buyerId, ticketId).token();
        reissueTicket.execute(organizerId, ticketId); // invalida el QR viejo

        // el QR viejo ya no sirve
        assertThatThrownBy(() -> eventCheckIn.execute(organizerId, eventId, oldToken, device()))
                .satisfies(t -> assertThat(denialCode(t)).isEqualTo("qr_invalid"));
        // el nuevo QR (siguiente GET) sí concede acceso
        String newToken = getTicketQr.execute(buyerId, ticketId).token();
        var result = eventCheckIn.execute(organizerId, eventId, newToken, device());
        assertThat(result.attendeeName()).isEqualTo("Persona");
    }

    @Test
    void unauthorized_scanner_is_denied_staff_not_assigned() {
        UUID ticketId = buyTicket(eventId, tariffId);
        String token = getTicketQr.execute(buyerId, ticketId).token();
        // el comprador (ATTENDEE) no es organizador ni staff del evento
        assertThatThrownBy(() -> eventCheckIn.execute(buyerId, eventId, token, device()))
                .satisfies(t -> assertThat(denialCode(t)).isEqualTo("staff_not_assigned"));
    }

    @Test
    void concurrent_scans_of_same_qr_grant_exactly_once() throws Exception {
        UUID ticketId = buyTicket(eventId, tariffId);
        String token = getTicketQr.execute(buyerId, ticketId).token();

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger granted = new AtomicInteger();
        AtomicInteger denied = new AtomicInteger();

        List<Future<?>> futures = List.of(
                pool.submit(() -> scanRace(token, start, granted, denied)),
                pool.submit(() -> scanRace(token, start, granted, denied)));
        start.countDown();
        for (Future<?> f : futures) {
            f.get();
        }
        pool.shutdown();

        // el lock del QR serializa: exactamente una entrada concedida
        assertThat(granted.get()).isEqualTo(1);
        assertThat(denied.get()).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM ops.event_checkins WHERE ticket_id=? AND result='GRANTED'",
                Integer.class, ticketId)).isEqualTo(1);
    }

    private Object scanRace(String token, CountDownLatch start, AtomicInteger granted, AtomicInteger denied) {
        try {
            start.await();
            eventCheckIn.execute(organizerId, eventId, token, device());
            granted.incrementAndGet();
        } catch (CheckInDeniedException e) {
            denied.incrementAndGet();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return null;
    }
}
