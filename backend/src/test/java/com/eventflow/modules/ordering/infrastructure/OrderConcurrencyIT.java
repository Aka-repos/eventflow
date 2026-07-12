package com.eventflow.modules.ordering.infrastructure;

import com.eventflow.modules.catalog.application.CreateCategoryUseCase;
import com.eventflow.modules.catalog.application.CreateEventUseCase;
import com.eventflow.modules.catalog.application.PublishEventUseCase;
import com.eventflow.modules.catalog.application.command.CreateEventCommand;
import com.eventflow.modules.identity.application.RegisterUserUseCase;
import com.eventflow.modules.identity.application.command.RegisterUserCommand;
import com.eventflow.modules.ordering.application.CreateOrderUseCase;
import com.eventflow.modules.ordering.application.PayOrderUseCase;
import com.eventflow.modules.ordering.application.command.CreateOrderCommand;
import com.eventflow.modules.ticketing.application.CreateTicketTypeUseCase;
import com.eventflow.modules.ticketing.application.command.TicketTypeCommand;
import com.eventflow.shared.domain.Money;
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

/**
 * Pruebas de carrera obligatorias (engineering/04 §5): el FOR UPDATE del inventario y los índices
 * únicos hacen la sobreventa y el doble cobro físicamente imposibles bajo concurrencia real.
 */
@Testcontainers
@SpringBootTest
@org.junit.jupiter.api.TestMethodOrder(org.junit.jupiter.api.MethodOrderer.MethodName.class)
class OrderConcurrencyIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    private static final int CAPACITY = 5;
    private static final int BUYERS = 12;

    private static UUID organizerId;
    private static UUID tariffId;
    private static List<UUID> buyerIds;

    @Autowired CreateOrderUseCase createOrder;
    @Autowired PayOrderUseCase payOrder;
    @Autowired JdbcTemplate jdbc;

    @BeforeAll
    static void setUpAll(@Autowired RegisterUserUseCase register, @Autowired CreateCategoryUseCase createCategory,
                         @Autowired CreateEventUseCase createEvent, @Autowired CreateTicketTypeUseCase createTariff,
                         @Autowired PublishEventUseCase publish, @Autowired JdbcTemplate jdbc) {
        organizerId = registerUser(register, jdbc, "conc-org@mail.com", "ORGANIZER");
        buyerIds = java.util.stream.IntStream.range(0, BUYERS)
                .mapToObj(i -> registerUser(register, jdbc, "conc-buyer" + i + "@mail.com", null))
                .toList();
        createCategory.execute("Concurrencia", null, true);
        short categoryId = jdbc.queryForObject(
                "SELECT id FROM catalog.categories WHERE name = 'Concurrencia'", Short.class);
        Instant starts = Instant.now().plus(30, ChronoUnit.DAYS);
        createEvent.execute(new CreateEventCommand(organizerId, "Evento Carrera", "d",
                categoryId, "Arena", null, null, null, "America/Panama", starts, starts.plusSeconds(7200)));
        UUID eventId = jdbc.queryForObject(
                "SELECT id FROM catalog.events WHERE title = 'Evento Carrera'", UUID.class);
        createTariff.execute(new TicketTypeCommand(organizerId, eventId, "Única", null,
                Money.of("20.00", "USD"), null, CAPACITY, null, null));
        tariffId = jdbc.queryForObject(
                "SELECT id FROM ticketing.ticket_types WHERE event_id = ?", UUID.class, eventId);
        publish.execute(organizerId, eventId);
    }

    private static UUID registerUser(RegisterUserUseCase register, JdbcTemplate jdbc, String email, String role) {
        register.execute(new RegisterUserCommand(email, "S3gura!pass", "Concurrente", "+50760000000"));
        if (role != null) {
            jdbc.update("""
                    INSERT INTO identity.user_roles (user_id, role_id)
                    SELECT u.id, r.id FROM identity.users u, identity.roles r
                    WHERE u.email = ? AND r.code = ?
                    """, email, role);
        }
        return jdbc.queryForObject("SELECT id FROM identity.users WHERE email = ?", UUID.class, email);
    }

    @Test
    void t1_concurrent_purchases_never_oversell() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(BUYERS);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger created = new AtomicInteger();
        AtomicInteger soldOut = new AtomicInteger();

        List<Future<?>> futures = buyerIds.stream().<Future<?>>map(buyer -> pool.submit(() -> {
            try {
                start.await();
                createOrder.execute(new CreateOrderCommand(buyer, UUID.randomUUID(),
                        List.of(new CreateOrderCommand.Item("TICKET", tariffId, 1))));
                created.incrementAndGet();
            } catch (com.eventflow.shared.error.DomainException e) {
                if ("event_sold_out".equals(e.errorCode().code())) {
                    soldOut.incrementAndGet();
                } else {
                    throw new IllegalStateException("Error inesperado en la carrera", e);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return null;
        })).toList();

        start.countDown();
        for (Future<?> f : futures) {
            f.get();
        }
        pool.shutdown();

        assertThat(created.get()).isEqualTo(CAPACITY);
        assertThat(soldOut.get()).isEqualTo(BUYERS - CAPACITY);
        // el CHECK sold BETWEEN 0 AND total + FOR UPDATE: jamás por encima del cupo
        Integer sold = jdbc.queryForObject(
                "SELECT sold_quantity FROM ticketing.ticket_types WHERE id = ?", Integer.class, tariffId);
        assertThat(sold).isEqualTo(CAPACITY);
        Integer pendingOrders = jdbc.queryForObject(
                "SELECT count(*) FROM commerce.orders WHERE status = 'PENDING'", Integer.class);
        assertThat(pendingOrders).isEqualTo(CAPACITY);
    }

    @Test
    void t2_concurrent_double_pay_settles_exactly_once() throws Exception {
        var row = jdbc.queryForMap(
                "SELECT id, buyer_id FROM commerce.orders WHERE status = 'PENDING' ORDER BY created_at LIMIT 1");
        UUID orderId = (UUID) row.get("id");
        UUID buyer = (UUID) row.get("buyer_id");

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger paid = new AtomicInteger();
        AtomicInteger conflicts = new AtomicInteger();

        List<Future<?>> futures = List.of(
                pool.submit(() -> payRace(buyer, orderId, start, paid, conflicts)),
                pool.submit(() -> payRace(buyer, orderId, start, paid, conflicts)));
        start.countDown();
        for (Future<?> f : futures) {
            f.get();
        }
        pool.shutdown();

        assertThat(paid.get()).isEqualTo(1);
        assertThat(conflicts.get()).isEqualTo(1);
        // A4: exactamente un pago liquidado, un solo juego de boletos y un solo asiento
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM commerce.payments WHERE order_id = ? AND status = 'APPROVED'",
                Integer.class, orderId)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM ticketing.tickets t JOIN commerce.order_items oi "
                        + "ON oi.id = t.source_order_item_id WHERE oi.order_id = ?",
                Integer.class, orderId)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM commerce.ledger_entries WHERE reference_id = ?",
                Integer.class, orderId)).isEqualTo(1);
    }

    private Object payRace(UUID buyer, UUID orderId, CountDownLatch start, AtomicInteger paid,
                           AtomicInteger conflicts) {
        try {
            start.await();
            payOrder.execute(buyer, orderId, "FAKE");
            paid.incrementAndGet();
        } catch (com.eventflow.shared.error.DomainException e) {
            conflicts.incrementAndGet();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return null;
    }
}
