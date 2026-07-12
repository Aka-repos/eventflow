package com.eventflow.recovery;

import com.eventflow.modules.catalog.application.CreateCategoryUseCase;
import com.eventflow.modules.catalog.application.CreateEventUseCase;
import com.eventflow.modules.catalog.application.PublishEventUseCase;
import com.eventflow.modules.catalog.application.command.CreateEventCommand;
import com.eventflow.modules.identity.application.RegisterUserUseCase;
import com.eventflow.modules.identity.application.command.RegisterUserCommand;
import com.eventflow.modules.ordering.application.CompleteApprovedOrdersUseCase;
import com.eventflow.modules.ordering.application.CreateOrderUseCase;
import com.eventflow.modules.ordering.application.ExpireOrdersUseCase;
import com.eventflow.modules.ordering.application.PayOrderUseCase;
import com.eventflow.modules.ordering.application.command.CreateOrderCommand;
import com.eventflow.modules.payments.application.PaymentsFacade;
import com.eventflow.modules.payments.domain.port.PaymentProvider;
import com.eventflow.modules.ticketing.application.CreateTicketTypeUseCase;
import com.eventflow.modules.ticketing.application.command.TicketTypeCommand;
import com.eventflow.shared.domain.Money;
import com.eventflow.shared.error.DomainException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
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

/**
 * IT de sistema (paquete neutral: orquesta ordering+payments+catalog, fuera de la matriz doc 10).
 * H1/H2: recuperabilidad del pago trifásico ante caídas (simuladas ejecutando fases sueltas),
 * reconciliación con la verdad del proveedor, reintentos idempotentes y ausencia de doble cobro.
 */
@Testcontainers
@SpringBootTest
@TestMethodOrder(MethodOrderer.MethodName.class)
class PaymentRecoveryIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    private static UUID buyerId;
    private static UUID tariffId;

    @Autowired CreateOrderUseCase createOrder;
    @Autowired PayOrderUseCase payOrder;
    @Autowired ExpireOrdersUseCase expireOrders;
    @Autowired CompleteApprovedOrdersUseCase completeApprovedOrders;
    @Autowired PaymentsFacade payments;
    @Autowired PaymentProvider provider;
    @Autowired JdbcTemplate jdbc;

    @BeforeAll
    static void setUpAll(@Autowired RegisterUserUseCase register, @Autowired CreateCategoryUseCase createCategory,
                         @Autowired CreateEventUseCase createEvent, @Autowired CreateTicketTypeUseCase createTariff,
                         @Autowired PublishEventUseCase publish, @Autowired JdbcTemplate jdbc) {
        register.execute(new RegisterUserCommand("rec-org@mail.com", "S3gura!pass", "Org", "+50760000000"));
        register.execute(new RegisterUserCommand("rec-buyer@mail.com", "S3gura!pass", "Buyer", "+50760000000"));
        jdbc.update("""
                INSERT INTO identity.user_roles (user_id, role_id)
                SELECT u.id, r.id FROM identity.users u, identity.roles r
                WHERE u.email = 'rec-org@mail.com' AND r.code = 'ORGANIZER'
                """);
        UUID organizerId = jdbc.queryForObject(
                "SELECT id FROM identity.users WHERE email = 'rec-org@mail.com'", UUID.class);
        buyerId = jdbc.queryForObject(
                "SELECT id FROM identity.users WHERE email = 'rec-buyer@mail.com'", UUID.class);
        createCategory.execute("Recovery", null, true);
        short categoryId = jdbc.queryForObject(
                "SELECT id FROM catalog.categories WHERE name = 'Recovery'", Short.class);
        Instant starts = Instant.now().plus(30, ChronoUnit.DAYS);
        createEvent.execute(new CreateEventCommand(organizerId, "Evento Recovery", "d", categoryId,
                "Arena", null, null, null, "America/Panama", starts, starts.plusSeconds(7200)));
        UUID eventId = jdbc.queryForObject(
                "SELECT id FROM catalog.events WHERE title = 'Evento Recovery'", UUID.class);
        createTariff.execute(new TicketTypeCommand(organizerId, eventId, "General", null,
                Money.of("20.00", "USD"), null, 50, null, null));
        tariffId = jdbc.queryForObject(
                "SELECT id FROM ticketing.ticket_types WHERE event_id = ?", UUID.class, eventId);
        publish.execute(organizerId, eventId);
    }

    private UUID newPendingOrder(int quantity) {
        return createOrder.execute(new CreateOrderCommand(buyerId, UUID.randomUUID(),
                List.of(new CreateOrderCommand.Item("TICKET", tariffId, quantity)))).order().getId();
    }

    private void backdatePayment(UUID orderId) {
        jdbc.update("UPDATE commerce.payments SET created_at = now() - interval '10 minutes' "
                + "WHERE order_id = ?", orderId);
    }

    private int sold() {
        return jdbc.queryForObject(
                "SELECT sold_quantity FROM ticketing.ticket_types WHERE id = ?", Integer.class, tariffId);
    }

    @Test
    void t1_crash_between_intent_and_authorization_reconciles_to_declined() {
        UUID orderId = newPendingOrder(1);
        int soldBefore = sold();

        // "caída" tras la fase 1: el intent PENDING quedó commiteado, el proveedor jamás fue invocado
        payments.createIntent(orderId, Money.of("20.00", "USD"), "FAKE");

        // el guard de expiración protege la orden mientras el intent siga abierto
        jdbc.update("UPDATE commerce.orders SET expires_at = now() - interval '1 minute' WHERE id = ?", orderId);
        expireOrders.execute();
        assertThat(jdbc.queryForObject("SELECT status FROM commerce.orders WHERE id = ?", String.class, orderId))
                .isEqualTo("PENDING");

        // reconciliación: lookup vacío ⇒ DECLINED seguro (jamás hubo cobro)
        backdatePayment(orderId);
        int resolved = payments.reconcileStaleIntents(10);
        assertThat(resolved).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT status FROM commerce.payments WHERE order_id = ?", String.class, orderId))
                .isEqualTo("DECLINED");

        // con el intent resuelto, la expiración ya puede liberar el inventario
        expireOrders.execute();
        assertThat(jdbc.queryForObject("SELECT status FROM commerce.orders WHERE id = ?", String.class, orderId))
                .isEqualTo("CANCELLED");
        assertThat(sold()).isEqualTo(soldBefore - 1);
    }

    @Test
    void t2_crash_after_authorization_reconciles_to_paid_with_tickets_and_ledger() {
        UUID orderId = newPendingOrder(2);
        int soldBefore = sold();

        // "caída" tras la fase 2: el proveedor SÍ cobró (queda en su registro), la fase 3 no corrió
        UUID intentId = payments.createIntent(orderId, Money.of("40.00", "USD"), "FAKE");
        provider.authorize(intentId, orderId, Money.of("40.00", "USD"), "FAKE");

        // reconciliación de intents: la verdad del proveedor ⇒ APPROVED
        backdatePayment(orderId);
        payments.reconcileStaleIntents(10);
        assertThat(jdbc.queryForObject(
                "SELECT status FROM commerce.payments WHERE order_id = ?", String.class, orderId))
                .isEqualTo("APPROVED");

        // reconciliación de órdenes: completa PAID + boletos + ledger + outbox
        jdbc.update("UPDATE commerce.orders SET created_at = now() - interval '5 minutes' WHERE id = ?", orderId);
        int completed = completeApprovedOrders.execute();
        assertThat(completed).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT status FROM commerce.orders WHERE id = ?", String.class, orderId))
                .isEqualTo("PAID");
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM ticketing.tickets t
                JOIN commerce.order_items oi ON oi.id = t.source_order_item_id
                WHERE oi.order_id = ?
                """, Integer.class, orderId)).isEqualTo(2);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM commerce.ledger_entries WHERE reference_id = ?", Integer.class, orderId))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM ops.outbox_events
                WHERE event_type = 'PaymentConfirmed' AND aggregate_id = ?
                """, Integer.class, orderId)).isEqualTo(1);
        // el inventario NO se libera: la venta se materializó
        assertThat(sold()).isEqualTo(soldBefore);
    }

    @Test
    void t3_retry_after_recovery_is_idempotent_and_never_double_charges() {
        // reintento de pago sobre la orden ya reconciliada como PAID (t2)
        UUID paidOrder = jdbc.queryForObject(
                "SELECT id FROM commerce.orders WHERE status = 'PAID' LIMIT 1", UUID.class);

        DomainException thrown = null;
        try {
            payOrder.execute(buyerId, paidOrder, "FAKE");
        } catch (DomainException e) {
            thrown = e;
        }
        assertThat(thrown).isNotNull();
        assertThat(thrown.errorCode().code()).isEqualTo("order_not_pending");

        // exactamente un pago liquidado y un juego de boletos — sin doble cobro
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM commerce.payments WHERE order_id = ? AND status IN ('APPROVED','REFUNDED')",
                Integer.class, paidOrder)).isEqualTo(1);
    }

    @Test
    void t4_reconciliation_is_idempotent() {
        int resolvedAgain = payments.reconcileStaleIntents(10);
        int completedAgain = completeApprovedOrders.execute();
        assertThat(resolvedAgain).isZero();
        assertThat(completedAgain).isZero();
    }

    @Test
    void t5_concurrent_pay_with_triphasic_flow_settles_exactly_once() throws Exception {
        UUID orderId = newPendingOrder(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger paid = new AtomicInteger();
        AtomicInteger conflicts = new AtomicInteger();

        List<Future<?>> futures = List.of(
                pool.submit(() -> race(orderId, start, paid, conflicts)),
                pool.submit(() -> race(orderId, start, paid, conflicts)));
        start.countDown();
        for (Future<?> f : futures) {
            f.get();
        }
        pool.shutdown();

        assertThat(paid.get()).isEqualTo(1);
        assertThat(conflicts.get()).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM commerce.payments WHERE order_id = ? AND status = 'APPROVED'",
                Integer.class, orderId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM ticketing.tickets t
                JOIN commerce.order_items oi ON oi.id = t.source_order_item_id
                WHERE oi.order_id = ?
                """, Integer.class, orderId)).isEqualTo(1);
    }

    private Object race(UUID orderId, CountDownLatch start, AtomicInteger paid, AtomicInteger conflicts) {
        try {
            start.await();
            payOrder.execute(buyerId, orderId, "FAKE");
            paid.incrementAndGet();
        } catch (DomainException e) {
            conflicts.incrementAndGet();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return null;
    }
}
