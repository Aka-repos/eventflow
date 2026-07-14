package com.eventflow.modules.refunds.api;

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

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * IT de contrato HTTP del Módulo 5 (MockMvc, PostgreSQL real): recovery-options, solicitud (201 +
 * Location), doble solicitud (409), seguridad por rol en aprobación, aprobación (200), idempotencia
 * de aprobación (409 refund_not_pending), listado del organizador y rechazo. Todo forma RFC 9457.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.MethodName.class)
class RefundContractIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    private static String organizerToken;
    private static String buyerToken;
    private static String eventId;
    private static String ticketId;      // se solicita/aprueba
    private static String ticketToReject; // se rechaza
    private static String refundId;

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbc;

    private String register(String role) throws Exception {
        String email = "rf-" + UUID.randomUUID() + "@mail.com";
        mockMvc.perform(post("/auth/register").contentType(APPLICATION_JSON).content("""
                {"email":"%s","password":"S3gura!pass","fullName":"Refund Persona","phone":"+50761234567"}
                """.formatted(email))).andExpect(status().isCreated());
        if (role != null) {
            jdbc.update("""
                    INSERT INTO identity.user_roles (user_id, role_id)
                    SELECT u.id, r.id FROM identity.users u, identity.roles r WHERE u.email=? AND r.code=?
                    """, email, role);
        }
        MvcResult login = mockMvc.perform(post("/auth/login").contentType(APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"S3gura!pass\"}"))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(login.getResponse().getContentAsString()).at("/data/accessToken").asText();
    }

    private JsonNode parse(MvcResult r) throws Exception {
        return objectMapper.readTree(r.getResponse().getContentAsString());
    }

    private String buyTicket(String tariffId) throws Exception {
        MvcResult order = mockMvc.perform(post("/orders").header("Authorization", "Bearer " + buyerToken)
                        .header("Idempotency-Key", UUID.randomUUID()).contentType(APPLICATION_JSON)
                        .content("{\"items\":[{\"type\":\"TICKET\",\"referenceId\":\"" + tariffId + "\",\"quantity\":1}]}"))
                .andExpect(status().isCreated()).andReturn();
        String orderId = parse(order).at("/data/id").asText();
        mockMvc.perform(post("/orders/" + orderId + "/pay").header("Authorization", "Bearer " + buyerToken)
                        .header("Idempotency-Key", UUID.randomUUID()).contentType(APPLICATION_JSON)
                        .content("{\"method\":\"FAKE\"}"))
                .andExpect(status().isOk());
        return jdbc.queryForObject("""
                SELECT t.id::text FROM ticketing.tickets t
                JOIN commerce.order_items oi ON oi.id = t.source_order_item_id WHERE oi.order_id = ?::uuid
                """, String.class, orderId);
    }

    @Test
    void t01_setup_event_with_open_refund_window() throws Exception {
        organizerToken = register("ORGANIZER");
        buyerToken = register(null);
        MvcResult cat = mockMvc.perform(post("/admin/categories")
                        .header("Authorization", "Bearer " + register("ADMIN"))
                        .contentType(APPLICATION_JSON).content("{\"name\":\"Cat-" + UUID.randomUUID() + "\",\"active\":true}"))
                .andExpect(status().isCreated()).andReturn();
        int categoryId = parse(cat).at("/data/id").asInt();
        Instant starts = Instant.now().plus(40, ChronoUnit.DAYS);
        MvcResult ev = mockMvc.perform(post("/organizer/events").header("Authorization", "Bearer " + organizerToken)
                        .contentType(APPLICATION_JSON).content("""
                                {"title":"Refund %s","description":"d","categoryId":%d,"venueName":"Arena",
                                 "timezone":"America/Panama","startsAt":"%s","endsAt":"%s"}
                                """.formatted(UUID.randomUUID(), categoryId, starts, starts.plusSeconds(7200))))
                .andExpect(status().isCreated()).andReturn();
        eventId = parse(ev).at("/data/id").asText();
        // ventana de reembolso ABIERTA (deadline dentro de 30 días)
        mockMvc.perform(put("/organizer/events/" + eventId + "/policy")
                        .header("Authorization", "Bearer " + organizerToken).header("If-Match", 0)
                        .contentType(APPLICATION_JSON).content("""
                                {"refundWindowEndsAt":"%s","exchangeEnabled":false,"exchangeDepreciationPct":10,
                                 "waitlistEnabled":false,"waitlistOfferMinutes":15,"tempReservationMinutes":10,
                                 "qrVisibilityHoursBefore":24,"qrExpirationMinutes":60}
                                """.formatted(Instant.now().plus(30, ChronoUnit.DAYS))))
                .andExpect(status().isOk());
        MvcResult tariff = mockMvc.perform(post("/organizer/events/" + eventId + "/ticket-types")
                        .header("Authorization", "Bearer " + organizerToken).contentType(APPLICATION_JSON)
                        .content("{\"name\":\"General\",\"price\":{\"amount\":\"30.00\",\"currency\":\"USD\"},\"totalQuantity\":50}"))
                .andExpect(status().isCreated()).andReturn();
        String tariffId = parse(tariff).at("/data/id").asText();
        mockMvc.perform(post("/organizer/events/" + eventId + "/publish")
                .header("Authorization", "Bearer " + organizerToken)).andExpect(status().isOk());
        ticketId = buyTicket(tariffId);
        ticketToReject = buyTicket(tariffId);
    }

    @Test
    void t02_recovery_options_offers_refund_within_window() throws Exception {
        mockMvc.perform(get("/tickets/" + ticketId + "/recovery-options")
                        .header("Authorization", "Bearer " + buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.option").value("REFUND"))
                .andExpect(jsonPath("$.data.refund.amount.amount").value("30.00"))
                .andExpect(jsonPath("$.data.links.action").value(
                        org.hamcrest.Matchers.endsWith("/refund-requests")));
    }

    @Test
    void t03_request_refund_returns_201_created_with_location() throws Exception {
        MvcResult res = mockMvc.perform(post("/tickets/" + ticketId + "/refund-requests")
                        .header("Authorization", "Bearer " + buyerToken)
                        .header("Idempotency-Key", UUID.randomUUID()).contentType(APPLICATION_JSON)
                        .content("{\"reason\":\"No podré asistir\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith("/refund-requests/")))
                .andExpect(jsonPath("$.data.status").value("REQUESTED"))
                .andExpect(jsonPath("$.data.ticketId").value(ticketId))
                .andExpect(jsonPath("$.data.amount.amount").value("30.00"))
                .andReturn();
        refundId = parse(res).at("/data/id").asText();
    }

    @Test
    void t04_double_request_is_conflict() throws Exception {
        mockMvc.perform(post("/tickets/" + ticketId + "/refund-requests")
                        .header("Authorization", "Bearer " + buyerToken)
                        .header("Idempotency-Key", UUID.randomUUID()).contentType(APPLICATION_JSON)
                        .content("{\"reason\":\"otra vez\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("refund_already_requested"));
    }

    @Test
    void t05_buyer_cannot_approve_refund() throws Exception {
        mockMvc.perform(post("/refund-requests/" + refundId + "/approve")
                        .header("Authorization", "Bearer " + buyerToken)
                        .header("Idempotency-Key", UUID.randomUUID()))
                .andExpect(status().isForbidden());
    }

    @Test
    void t06_organizer_approves_refund() throws Exception {
        mockMvc.perform(post("/refund-requests/" + refundId + "/approve")
                        .header("Authorization", "Bearer " + organizerToken)
                        .header("Idempotency-Key", UUID.randomUUID()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"))
                .andExpect(jsonPath("$.data.resolvedAt").isNotEmpty());
        // el boleto quedó REFUNDED
        org.assertj.core.api.Assertions.assertThat(
                jdbc.queryForObject("SELECT status FROM ticketing.tickets WHERE id=?::uuid", String.class, ticketId))
                .isEqualTo("REFUNDED");
    }

    @Test
    void t07_approve_again_is_conflict_refund_not_pending() throws Exception {
        mockMvc.perform(post("/refund-requests/" + refundId + "/approve")
                        .header("Authorization", "Bearer " + organizerToken)
                        .header("Idempotency-Key", UUID.randomUUID()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("refund_not_pending"));
    }

    @Test
    void t08_organizer_lists_event_refunds() throws Exception {
        mockMvc.perform(get("/organizer/events/" + eventId + "/refund-requests")
                        .header("Authorization", "Bearer " + organizerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].id").isNotEmpty());
    }

    @Test
    void t09_reject_flow_returns_ticket_to_active() throws Exception {
        MvcResult req = mockMvc.perform(post("/tickets/" + ticketToReject + "/refund-requests")
                        .header("Authorization", "Bearer " + buyerToken)
                        .header("Idempotency-Key", UUID.randomUUID()).contentType(APPLICATION_JSON)
                        .content("{\"reason\":\"me arrepentí\"}"))
                .andExpect(status().isCreated()).andReturn();
        String id = parse(req).at("/data/id").asText();

        mockMvc.perform(post("/refund-requests/" + id + "/reject")
                        .header("Authorization", "Bearer " + organizerToken)
                        .header("Idempotency-Key", UUID.randomUUID()).contentType(APPLICATION_JSON)
                        .content("{\"reason\":\"El evento no admite reembolso manual\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REJECTED"));

        org.assertj.core.api.Assertions.assertThat(
                jdbc.queryForObject("SELECT status FROM ticketing.tickets WHERE id=?::uuid", String.class, ticketToReject))
                .isEqualTo("ACTIVE");
    }

    @Test
    void t10_reject_requires_reason() throws Exception {
        mockMvc.perform(post("/refund-requests/" + UUID.randomUUID() + "/reject")
                        .header("Authorization", "Bearer " + organizerToken)
                        .header("Idempotency-Key", UUID.randomUUID()).contentType(APPLICATION_JSON)
                        .content("{\"reason\":\"\"}"))
                .andExpect(status().isUnprocessableEntity());
    }
}
