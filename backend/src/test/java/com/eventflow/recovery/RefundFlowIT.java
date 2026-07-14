package com.eventflow.recovery;

import com.eventflow.modules.catalog.application.CreateCategoryUseCase;
import com.eventflow.modules.catalog.application.CreateEventUseCase;
import com.eventflow.modules.catalog.application.PublishEventUseCase;
import com.eventflow.modules.catalog.application.UpdateEventPolicyUseCase;
import com.eventflow.modules.catalog.application.command.CreateEventCommand;
import com.eventflow.modules.catalog.application.command.UpdatePolicyCommand;
import com.eventflow.modules.identity.application.RegisterUserUseCase;
import com.eventflow.modules.identity.application.command.RegisterUserCommand;
import com.eventflow.modules.ordering.application.CreateOrderUseCase;
import com.eventflow.modules.ordering.application.PayOrderUseCase;
import com.eventflow.modules.ordering.application.command.CreateOrderCommand;
import com.eventflow.modules.refunds.application.ApproveRefundUseCase;
import com.eventflow.modules.refunds.application.GetRecoveryOptionsUseCase;
import com.eventflow.modules.refunds.application.RejectRefundUseCase;
import com.eventflow.modules.refunds.application.RequestRefundUseCase;
import com.eventflow.modules.refunds.domain.exception.RefundAlreadyRequestedException;
import com.eventflow.modules.ticketing.application.CreateTicketTypeUseCase;
import com.eventflow.modules.ticketing.application.command.TicketTypeCommand;
import com.eventflow.modules.ticketing.domain.RecoveryPolicy;
import com.eventflow.modules.ticketing.domain.exception.RefundWindowClosedException;
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
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * IT de sistema del Módulo 5 (S3): compra real → recovery-options → solicitud → aprobación/rechazo,
 * con inventario, ledger (D1 ORGANIZER→BUYER), pago REFUNDED, outbox y ADR-19. PostgreSQL 17 real.
 */
@Testcontainers
@SpringBootTest
class RefundFlowIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    private static UUID organizerId;
    private static UUID buyerId;
    private static UUID eventId;
    private static UUID tariffId;
    private static UUID noRefundEventId;
    private static UUID noRefundTariffId;

    @Autowired CreateOrderUseCase createOrder;
    @Autowired PayOrderUseCase payOrder;
    @Autowired GetRecoveryOptionsUseCase getRecoveryOptions;
    @Autowired RequestRefundUseCase requestRefund;
    @Autowired ApproveRefundUseCase approveRefund;
    @Autowired RejectRefundUseCase rejectRefund;
    @Autowired JdbcTemplate jdbc;

    @BeforeAll
    static void setUpAll(@Autowired RegisterUserUseCase register, @Autowired CreateCategoryUseCase createCategory,
                         @Autowired CreateEventUseCase createEvent, @Autowired CreateTicketTypeUseCase createTariff,
                         @Autowired UpdateEventPolicyUseCase updatePolicy, @Autowired PublishEventUseCase publish,
                         @Autowired JdbcTemplate jdbc) {
        organizerId = user(register, jdbc, "rf-org@mail.com", "ORGANIZER");
        buyerId = user(register, jdbc, "rf-buyer@mail.com", null);
        createCategory.execute("Refunds", null, true);
        short cat = jdbc.queryForObject("SELECT id FROM catalog.categories WHERE name='Refunds'", Short.class);
        Instant starts = Instant.now().plus(30, ChronoUnit.DAYS);
        // evento con ventana de reembolso ABIERTA (deadline lejano)
        eventId = event(createEvent, jdbc, cat, "Evento Refund", starts);
        setPolicy(updatePolicy, eventId, starts.plus(20, ChronoUnit.DAYS)); // refund abierto
        tariffId = tariff(createTariff, publish, jdbc, eventId);
        // evento con ventana CERRADA y exchange deshabilitado (recovery = NONE)
        noRefundEventId = event(createEvent, jdbc, cat, "Evento Sin Refund", starts);
        setPolicy(updatePolicy, noRefundEventId, Instant.now().minus(1, ChronoUnit.DAYS)); // refund cerrado
        noRefundTariffId = tariff(createTariff, publish, jdbc, noRefundEventId);
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

    private static UUID event(CreateEventUseCase createEvent, JdbcTemplate jdbc, short cat, String title,
                              Instant starts) {
        createEvent.execute(new CreateEventCommand(organizerId, title, "d", cat, "Arena", null, null, null,
                "America/Panama", starts, starts.plusSeconds(7200)));
        return jdbc.queryForObject("SELECT id FROM catalog.events WHERE title=?", UUID.class, title);
    }

    private static void setPolicy(UpdateEventPolicyUseCase updatePolicy, UUID eventId, Instant refundEndsAt) {
        updatePolicy.execute(new UpdatePolicyCommand(organizerId, eventId, 0, refundEndsAt, false, 10, null,
                false, 15, 10, 24, 60, null, java.util.Map.of()));
    }

    private static UUID tariff(CreateTicketTypeUseCase createTariff, PublishEventUseCase publish,
                               JdbcTemplate jdbc, UUID eventId) {
        createTariff.execute(new TicketTypeCommand(organizerId, eventId, "General", null,
                Money.of("40.00", "USD"), null, 50, null, null));
        UUID id = jdbc.queryForObject("SELECT id FROM ticketing.ticket_types WHERE event_id=?", UUID.class, eventId);
        publish.execute(organizerId, eventId);
        return id;
    }

    private UUID buyTicket(UUID tariffId) {
        UUID orderId = createOrder.execute(new CreateOrderCommand(buyerId, UUID.randomUUID(),
                List.of(new CreateOrderCommand.Item("TICKET", tariffId, 1)))).order().getId();
        payOrder.execute(buyerId, orderId, "FAKE");
        return jdbc.queryForObject("""
                SELECT t.id FROM ticketing.tickets t JOIN commerce.order_items oi ON oi.id = t.source_order_item_id
                WHERE oi.order_id = ?
                """, UUID.class, orderId);
    }

    private int sold(UUID tariffId) {
        return jdbc.queryForObject("SELECT sold_quantity FROM ticketing.ticket_types WHERE id=?",
                Integer.class, tariffId);
    }

    @Test
    void t1_recovery_options_offers_refund_within_window() {
        UUID ticketId = buyTicket(tariffId);
        var result = getRecoveryOptions.execute(buyerId, ticketId);
        assertThat(result.recovery().option()).isEqualTo(RecoveryPolicy.Option.REFUND);
        assertThat(result.recovery().refundAmount()).isEqualTo(Money.of("40.00", "USD"));
    }

    @Test
    void t2_full_refund_flow_approve_returns_inventory_ledger_payment_outbox() {
        int soldBefore = sold(tariffId);
        UUID ticketId = buyTicket(tariffId);
        assertThat(sold(tariffId)).isEqualTo(soldBefore + 1);

        // solicitud: ticket → REFUND_PENDING, QR bloqueado (si existiera), expediente REQUESTED
        var refund = requestRefund.execute(buyerId, ticketId, "No podré asistir");
        assertThat(jdbc.queryForObject("SELECT status FROM ticketing.tickets WHERE id=?", String.class, ticketId))
                .isEqualTo("REFUND_PENDING");
        assertThat(jdbc.queryForObject("SELECT status FROM commerce.refund_requests WHERE id=?",
                String.class, refund.getId())).isEqualTo("REQUESTED");
        // el pago de adquisición quedó referenciado (C2) y el monto congelado
        assertThat(refund.getAmount()).isEqualTo(Money.of("40.00", "USD"));

        // aprobación: ticket → REFUNDED, pago → REFUNDED, cupo devuelto, asiento ORGANIZER→BUYER
        approveRefund.execute(organizerId, refund.getId());
        assertThat(jdbc.queryForObject("SELECT status FROM ticketing.tickets WHERE id=?", String.class, ticketId))
                .isEqualTo("REFUNDED");
        assertThat(jdbc.queryForObject("SELECT status FROM commerce.refund_requests WHERE id=?",
                String.class, refund.getId())).isEqualTo("APPROVED");
        assertThat(sold(tariffId)).isEqualTo(soldBefore); // cupo de vuelta
        assertThat(jdbc.queryForObject(
                "SELECT status FROM commerce.payments WHERE id=?", String.class, refund.getPaymentId()))
                .isEqualTo("REFUNDED");
        // D1: asiento REFUND ORGANIZER→BUYER por 40.00
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM commerce.ledger_entries
                WHERE entry_type='REFUND' AND source_account LIKE 'ORGANIZER:%'
                  AND destination_account LIKE 'BUYER:%' AND amount=40.00 AND reference_id=?
                """, Integer.class, refund.getId())).isEqualTo(1);
        // outbox: RefundApproved + TicketRefunded + TicketReleased
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM ops.outbox_events WHERE event_type='TicketReleased' AND aggregate_id=?",
                Integer.class, ticketId)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM ops.outbox_events WHERE event_type IN ('RefundApproved','TicketRefunded')",
                Integer.class)).isGreaterThanOrEqualTo(2);
        // historia del boleto
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM ticketing.ticket_history WHERE ticket_id=? AND cause='REFUND_APPROVED'",
                Integer.class, ticketId)).isEqualTo(1);
    }

    @Test
    void t3_reject_returns_ticket_to_active_no_money_moved() {
        int soldBefore = sold(tariffId);
        UUID ticketId = buyTicket(tariffId);
        var refund = requestRefund.execute(buyerId, ticketId, "Me arrepentí");
        assertThat(sold(tariffId)).isEqualTo(soldBefore + 1);

        rejectRefund.execute(organizerId, refund.getId(), "El evento no admite reembolso manual");

        assertThat(jdbc.queryForObject("SELECT status FROM ticketing.tickets WHERE id=?", String.class, ticketId))
                .isEqualTo("ACTIVE"); // vuelve a activo
        assertThat(jdbc.queryForObject("SELECT status FROM commerce.refund_requests WHERE id=?",
                String.class, refund.getId())).isEqualTo("REJECTED");
        assertThat(sold(tariffId)).isEqualTo(soldBefore + 1); // el cupo NO se libera
        // ningún asiento ni pago reembolsado por este ticket
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM commerce.payments WHERE id=? AND status='REFUNDED'",
                Integer.class, refund.getPaymentId())).isZero();
    }

    @Test
    void t4_adr19_exchange_acquired_cannot_refund() {
        // fabricamos un boleto EXCHANGE-adquirido (M6 lo hará de verdad; aquí forzamos acquired_via)
        UUID ticketId = buyTicket(tariffId);
        jdbc.update("UPDATE ticketing.tickets SET acquired_via='EXCHANGE' WHERE id=?", ticketId);

        assertThatThrownBy(() -> requestRefund.execute(buyerId, ticketId, "intento"))
                .satisfies(t -> assertThat(((DomainException) t).errorCode().code())
                        .isEqualTo("refund_not_allowed_exchange_acquired"));
        // el boleto no cambió de estado
        assertThat(jdbc.queryForObject("SELECT status FROM ticketing.tickets WHERE id=?", String.class, ticketId))
                .isEqualTo("ACTIVE");
    }

    @Test
    void t5_refund_window_closed_is_rejected() {
        UUID ticketId = buyTicket(noRefundTariffId);
        assertThatThrownBy(() -> requestRefund.execute(buyerId, ticketId, "tarde"))
                .isInstanceOf(RefundWindowClosedException.class);
    }

    @Test
    void t6_double_request_is_conflict_and_concurrent_request_settles_once() throws Exception {
        UUID ticketId = buyTicket(tariffId);
        // primera solicitud OK
        requestRefund.execute(buyerId, ticketId, "primera");
        // segunda secuencial → 409
        assertThatThrownBy(() -> requestRefund.execute(buyerId, ticketId, "segunda"))
                .isInstanceOf(RefundAlreadyRequestedException.class);

        // concurrencia: dos solicitudes sobre OTRO boleto → exactamente una crea el expediente
        UUID ticket2 = buyTicket(tariffId);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger created = new AtomicInteger();
        AtomicInteger conflicts = new AtomicInteger();
        List<Future<?>> futures = List.of(
                pool.submit(() -> race(ticket2, start, created, conflicts)),
                pool.submit(() -> race(ticket2, start, created, conflicts)));
        start.countDown();
        for (Future<?> f : futures) {
            f.get();
        }
        pool.shutdown();
        assertThat(created.get()).isEqualTo(1);
        assertThat(conflicts.get()).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM commerce.refund_requests WHERE ticket_id=? AND status='REQUESTED'",
                Integer.class, ticket2)).isEqualTo(1);
    }

    @Test
    void t7_double_approve_settles_once() {
        UUID ticketId = buyTicket(tariffId);
        var refund = requestRefund.execute(buyerId, ticketId, "x");
        approveRefund.execute(organizerId, refund.getId());
        // segundo approve → refund_not_pending
        assertThatThrownBy(() -> approveRefund.execute(organizerId, refund.getId()))
                .satisfies(t -> assertThat(((DomainException) t).errorCode().code())
                        .isEqualTo("refund_not_pending"));
        // un solo asiento y un solo pago reembolsado
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM commerce.ledger_entries WHERE reference_id=?", Integer.class,
                refund.getId())).isEqualTo(1);
    }

    private Object race(UUID ticketId, CountDownLatch start, AtomicInteger created, AtomicInteger conflicts) {
        try {
            start.await();
            requestRefund.execute(buyerId, ticketId, "carrera");
            created.incrementAndGet();
        } catch (RefundAlreadyRequestedException e) {
            conflicts.incrementAndGet();
        } catch (DomainException e) {
            conflicts.incrementAndGet();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return null;
    }
}
