package com.eventflow.modules.ordering.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * IT de contrato del Módulo 3 contra PostgreSQL 17 real (Flyway V1–V9): compra primaria completa
 * (S2), idempotencia ADR-07, expiración con liberación de inventario, ledger, outbox e historial.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.MethodName.class)
class OrderFlowIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    private static final Instant STARTS = Instant.now().plus(60, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);

    private static String organizerToken;
    private static String buyerToken;
    private static UUID buyerId;
    private static String eventId;
    private static String tariffId;
    private static String orderId;
    private static String paidOrderId;
    private static String ticketId;

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbc;

    private String register(String role) throws Exception {
        String email = "ord-" + UUID.randomUUID() + "@mail.com";
        mockMvc.perform(post("/auth/register").contentType(APPLICATION_JSON).content("""
                        {"email":"%s","password":"S3gura!pass","fullName":"Comprador Prueba","phone":"+50761234567"}
                        """.formatted(email)))
                .andExpect(status().isCreated());
        if (role != null) {
            jdbc.update("""
                    INSERT INTO identity.user_roles (user_id, role_id)
                    SELECT u.id, r.id FROM identity.users u, identity.roles r
                    WHERE u.email = ? AND r.code = ?
                    """, email, role);
        }
        MvcResult login = mockMvc.perform(post("/auth/login").contentType(APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"S3gura!pass\"}"))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(login.getResponse().getContentAsString()).at("/data/accessToken").asText();
    }

    private JsonNode parse(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private String createOrderBody(int quantity) {
        return """
                {"items":[{"type":"TICKET","referenceId":"%s","quantity":%d}]}
                """.formatted(tariffId, quantity);
    }

    @Test
    void t01_setup_event_with_limited_inventory() throws Exception {
        organizerToken = register("ORGANIZER");
        buyerToken = register(null);
        buyerId = UUID.fromString(jdbc.queryForObject(
                "SELECT id::text FROM identity.users ORDER BY created_at DESC LIMIT 1", String.class));

        MvcResult cat = mockMvc.perform(post("/admin/categories")
                        .header("Authorization", "Bearer " + register("ADMIN"))
                        .contentType(APPLICATION_JSON)
                        .content("{\"name\":\"Ordenes\",\"active\":true}"))
                .andExpect(status().isCreated()).andReturn();
        int categoryId = parse(cat).at("/data/id").asInt();

        MvcResult event = mockMvc.perform(post("/organizer/events")
                        .header("Authorization", "Bearer " + organizerToken)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"title":"Concierto de Órdenes","description":"Prueba compra",
                                 "categoryId":%d,"venueName":"Arena","timezone":"America/Panama",
                                 "startsAt":"%s","endsAt":"%s"}
                                """.formatted(categoryId, STARTS, STARTS.plusSeconds(7200))))
                .andExpect(status().isCreated()).andReturn();
        eventId = parse(event).at("/data/id").asText();

        MvcResult tariff = mockMvc.perform(post("/organizer/events/" + eventId + "/ticket-types")
                        .header("Authorization", "Bearer " + organizerToken)
                        .contentType(APPLICATION_JSON)
                        .content("{\"name\":\"General\",\"price\":{\"amount\":\"40.00\",\"currency\":\"USD\"},\"totalQuantity\":5}"))
                .andExpect(status().isCreated()).andReturn();
        tariffId = parse(tariff).at("/data/id").asText();

        mockMvc.perform(post("/organizer/events/" + eventId + "/publish")
                        .header("Authorization", "Bearer " + organizerToken))
                .andExpect(status().isOk());
    }

    @Test
    void t02_create_order_requires_idempotency_key() throws Exception {
        mockMvc.perform(post("/orders")
                        .header("Authorization", "Bearer " + buyerToken)
                        .contentType(APPLICATION_JSON)
                        .content(createOrderBody(1)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("idempotency_key_required"));
    }

    @Test
    void t03_create_order_reserves_inventory_and_replays_on_same_key() throws Exception {
        UUID key = UUID.randomUUID();
        MvcResult created = mockMvc.perform(post("/orders")
                        .header("Authorization", "Bearer " + buyerToken)
                        .header("Idempotency-Key", key)
                        .contentType(APPLICATION_JSON)
                        .content(createOrderBody(2)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.total.amount").value("80.00"))
                .andExpect(jsonPath("$.data.items[0].description").value("General — Concierto de Órdenes"))
                .andExpect(jsonPath("$.data.expiresAt").isNotEmpty())
                .andReturn();
        orderId = parse(created).at("/data/id").asText();

        // inventario decrementado al crear (S2)
        Integer sold = jdbc.queryForObject(
                "SELECT sold_quantity FROM ticketing.ticket_types WHERE id = ?::uuid", Integer.class, tariffId);
        assertThat(sold).isEqualTo(2);

        // replay con la MISMA clave y mismo cuerpo ⇒ misma orden, sin doble reserva
        MvcResult replay = mockMvc.perform(post("/orders")
                        .header("Authorization", "Bearer " + buyerToken)
                        .header("Idempotency-Key", key)
                        .contentType(APPLICATION_JSON)
                        .content(createOrderBody(2)))
                .andExpect(status().isCreated()).andReturn();
        assertThat(parse(replay).at("/data/id").asText()).isEqualTo(orderId);
        assertThat(jdbc.queryForObject(
                "SELECT sold_quantity FROM ticketing.ticket_types WHERE id = ?::uuid", Integer.class, tariffId))
                .isEqualTo(2);

        // misma clave con cuerpo distinto ⇒ 422 idempotency_key_reuse
        mockMvc.perform(post("/orders")
                        .header("Authorization", "Bearer " + buyerToken)
                        .header("Idempotency-Key", key)
                        .contentType(APPLICATION_JSON)
                        .content(createOrderBody(1)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("idempotency_key_reuse"));
    }

    @Test
    void t04_oversell_is_physically_impossible() throws Exception {
        // quedan 3 de 5; pedir 4 ⇒ 409 event_sold_out
        mockMvc.perform(post("/orders")
                        .header("Authorization", "Bearer " + buyerToken)
                        .header("Idempotency-Key", UUID.randomUUID())
                        .contentType(APPLICATION_JSON)
                        .content(createOrderBody(4)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("event_sold_out"));
    }

    @Test
    void t05_pay_order_issues_tickets_ledger_and_outbox() throws Exception {
        MvcResult paid = mockMvc.perform(post("/orders/" + orderId + "/pay")
                        .header("Authorization", "Bearer " + buyerToken)
                        .header("Idempotency-Key", UUID.randomUUID())
                        .contentType(APPLICATION_JSON)
                        .content("{\"method\":\"FAKE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PAID"))
                .andExpect(jsonPath("$.data.payment.status").value("APPROVED"))
                .andExpect(jsonPath("$.data.payment.provider").value("FAKE"))
                .andExpect(jsonPath("$.data.items[0].ticketIds.length()").value(2))
                .andReturn();
        paidOrderId = orderId;
        ticketId = parse(paid).at("/data/items/0/ticketIds/0").asText();

        // boletos ACTIVE con snapshot; historial ISSUED; ledger SALE; outbox PaymentConfirmed+TicketPurchased
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ticketing.tickets WHERE status='ACTIVE'",
                Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject(
                "SELECT policy_snapshot ->> 'qrVisibilityHoursBefore' FROM ticketing.tickets LIMIT 1",
                String.class)).isEqualTo("24");
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM ticketing.ticket_history WHERE cause='ISSUED'", Integer.class))
                .isEqualTo(2);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM commerce.ledger_entries WHERE entry_type='SALE' AND amount=80.00",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM ops.outbox_events WHERE event_type IN ('PaymentConfirmed')",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM ops.outbox_events WHERE event_type = 'TicketPurchased'",
                Integer.class)).isEqualTo(2);
    }

    @Test
    void t06_paid_order_cannot_be_paid_or_cancelled_again() throws Exception {
        mockMvc.perform(post("/orders/" + paidOrderId + "/pay")
                        .header("Authorization", "Bearer " + buyerToken)
                        .header("Idempotency-Key", UUID.randomUUID())
                        .contentType(APPLICATION_JSON)
                        .content("{\"method\":\"FAKE\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("order_not_pending"));

        mockMvc.perform(post("/orders/" + paidOrderId + "/cancel")
                        .header("Authorization", "Bearer " + buyerToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("order_not_pending"));
    }

    @Test
    void t07_declined_payment_fails_order_and_releases_inventory() throws Exception {
        // tarifa con precio .13 ⇒ FakePaymentProvider rechaza (gancho documentado)
        MvcResult tariff13 = mockMvc.perform(post("/organizer/events/" + eventId + "/ticket-types")
                        .header("Authorization", "Bearer " + organizerToken)
                        .contentType(APPLICATION_JSON)
                        .content("{\"name\":\"Gafas Negras\",\"price\":{\"amount\":\"13.13\",\"currency\":\"USD\"},\"totalQuantity\":10}"))
                .andExpect(status().isCreated()).andReturn();
        String unluckyTariff = parse(tariff13).at("/data/id").asText();

        MvcResult order = mockMvc.perform(post("/orders")
                        .header("Authorization", "Bearer " + buyerToken)
                        .header("Idempotency-Key", UUID.randomUUID())
                        .contentType(APPLICATION_JSON)
                        .content("{\"items\":[{\"type\":\"TICKET\",\"referenceId\":\"" + unluckyTariff + "\",\"quantity\":1}]}"))
                .andExpect(status().isCreated()).andReturn();
        String failingOrder = parse(order).at("/data/id").asText();

        mockMvc.perform(post("/orders/" + failingOrder + "/pay")
                        .header("Authorization", "Bearer " + buyerToken)
                        .header("Idempotency-Key", UUID.randomUUID())
                        .contentType(APPLICATION_JSON)
                        .content("{\"method\":\"CARD\"}"))
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.code").value("payment_failed"))
                .andExpect(jsonPath("$.detail").value("La tarjeta fue rechazada por el emisor"));

        // orden FAILED persistida (no revertida por el 402) e inventario liberado
        mockMvc.perform(get("/orders/" + failingOrder).header("Authorization", "Bearer " + buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("FAILED"))
                .andExpect(jsonPath("$.data.payment.status").value("DECLINED"));
        assertThat(jdbc.queryForObject(
                "SELECT sold_quantity FROM ticketing.ticket_types WHERE id = ?::uuid",
                Integer.class, unluckyTariff)).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM ops.outbox_events WHERE event_type='PaymentFailed'", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void t08_cancel_releases_inventory_and_expiration_scheduler_is_idempotent() throws Exception {
        MvcResult order = mockMvc.perform(post("/orders")
                        .header("Authorization", "Bearer " + buyerToken)
                        .header("Idempotency-Key", UUID.randomUUID())
                        .contentType(APPLICATION_JSON)
                        .content(createOrderBody(1)))
                .andExpect(status().isCreated()).andReturn();
        String cancelable = parse(order).at("/data/id").asText();
        Integer soldBefore = jdbc.queryForObject(
                "SELECT sold_quantity FROM ticketing.ticket_types WHERE id = ?::uuid", Integer.class, tariffId);

        mockMvc.perform(post("/orders/" + cancelable + "/cancel")
                        .header("Authorization", "Bearer " + buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));
        assertThat(jdbc.queryForObject(
                "SELECT sold_quantity FROM ticketing.ticket_types WHERE id = ?::uuid", Integer.class, tariffId))
                .isEqualTo(soldBefore - 1);

        // expiración: orden PENDING con expires_at vencido ⇒ /pay la materializa como CANCELLED + libera
        MvcResult stale = mockMvc.perform(post("/orders")
                        .header("Authorization", "Bearer " + buyerToken)
                        .header("Idempotency-Key", UUID.randomUUID())
                        .contentType(APPLICATION_JSON)
                        .content(createOrderBody(1)))
                .andExpect(status().isCreated()).andReturn();
        String staleOrder = parse(stale).at("/data/id").asText();
        jdbc.update("UPDATE commerce.orders SET expires_at = now() - interval '1 minute' WHERE id = ?::uuid",
                staleOrder);

        mockMvc.perform(post("/orders/" + staleOrder + "/pay")
                        .header("Authorization", "Bearer " + buyerToken)
                        .header("Idempotency-Key", UUID.randomUUID())
                        .contentType(APPLICATION_JSON)
                        .content("{\"method\":\"FAKE\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("order_expired"));
        assertThat(jdbc.queryForObject(
                "SELECT status FROM commerce.orders WHERE id = ?::uuid", String.class, staleOrder))
                .isEqualTo("CANCELLED");
        assertThat(jdbc.queryForObject(
                "SELECT sold_quantity FROM ticketing.ticket_types WHERE id = ?::uuid", Integer.class, tariffId))
                .isEqualTo(soldBefore - 1);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM ops.outbox_events WHERE event_type='OrderCancelled'", Integer.class))
                .isEqualTo(2);
    }

    @Test
    void t09_my_tickets_and_detail_with_history() throws Exception {
        mockMvc.perform(get("/tickets").header("Authorization", "Bearer " + buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].event.title").value("Concierto de Órdenes"))
                .andExpect(jsonPath("$.data[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.data[0].acquiredVia").value("PRIMARY"))
                .andExpect(jsonPath("$.data[0].qrAvailableAt").isNotEmpty());

        mockMvc.perform(get("/tickets/" + ticketId).header("Authorization", "Bearer " + buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.originalPrice.amount").value("40.00"))
                .andExpect(jsonPath("$.data.acquisitionPrice.amount").value("40.00"))
                .andExpect(jsonPath("$.data.history[0].cause").value("ISSUED"));

        // anti-enumeración: el boleto de otro usuario es 404
        mockMvc.perform(get("/tickets/" + ticketId).header("Authorization", "Bearer " + organizerToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void t10_order_history_and_security() throws Exception {
        mockMvc.perform(get("/orders").header("Authorization", "Bearer " + buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(4))
                .andExpect(jsonPath("$.meta.hasNext").value(false));

        mockMvc.perform(get("/orders").param("status", "PAID")
                        .header("Authorization", "Bearer " + buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].items[0].ticketIds.length()").value(2));

        // órdenes ajenas: 404 anti-enumeración; sin token: 401
        mockMvc.perform(get("/orders/" + paidOrderId).header("Authorization", "Bearer " + organizerToken))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/orders")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/orders/" + paidOrderId + "/pay")
                        .header("Authorization", "Bearer " + organizerToken)
                        .header("Idempotency-Key", UUID.randomUUID())
                        .contentType(APPLICATION_JSON)
                        .content("{\"method\":\"FAKE\"}"))
                .andExpect(status().isNotFound());
    }
}
