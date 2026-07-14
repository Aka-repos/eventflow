package com.eventflow.modules.checkin.api;

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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * IT de contrato HTTP del Módulo 4 (MockMvc, PostgreSQL real): seguridad por rol, ventana de
 * visibilidad, forma del QR, check-in GRANTED y rechazos como Problem RFC 9457 con su code.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.MethodName.class)
class QrCheckInContractIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    private static String organizerToken;
    private static String buyerToken;
    private static String eventId;
    private static String futureEventId;
    private static String ticketId;
    private static String futureTicketId;

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbc;

    private String register(String role) throws Exception {
        String email = "qr-" + UUID.randomUUID() + "@mail.com";
        mockMvc.perform(post("/auth/register").contentType(APPLICATION_JSON).content("""
                {"email":"%s","password":"S3gura!pass","fullName":"QR Persona","phone":"+50761234567"}
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

    private String buyTicket(String eventId, String tariffId, String token) throws Exception {
        MvcResult order = mockMvc.perform(post("/orders").header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", UUID.randomUUID()).contentType(APPLICATION_JSON)
                        .content("{\"items\":[{\"type\":\"TICKET\",\"referenceId\":\"" + tariffId + "\",\"quantity\":1}]}"))
                .andExpect(status().isCreated()).andReturn();
        String orderId = parse(order).at("/data/id").asText();
        mockMvc.perform(post("/orders/" + orderId + "/pay").header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", UUID.randomUUID()).contentType(APPLICATION_JSON)
                        .content("{\"method\":\"FAKE\"}"))
                .andExpect(status().isOk());
        return jdbc.queryForObject("""
                SELECT t.id::text FROM ticketing.tickets t
                JOIN commerce.order_items oi ON oi.id = t.source_order_item_id WHERE oi.order_id = ?::uuid
                """, String.class, orderId);
    }

    private String setUpEvent(String title, Instant starts, int visibilityHours) throws Exception {
        MvcResult cat = mockMvc.perform(post("/admin/categories")
                        .header("Authorization", "Bearer " + register("ADMIN"))
                        .contentType(APPLICATION_JSON).content("{\"name\":\"Cat-" + title + "\",\"active\":true}"))
                .andExpect(status().isCreated()).andReturn();
        int categoryId = parse(cat).at("/data/id").asInt();
        MvcResult ev = mockMvc.perform(post("/organizer/events").header("Authorization", "Bearer " + organizerToken)
                        .contentType(APPLICATION_JSON).content("""
                                {"title":"%s","description":"d","categoryId":%d,"venueName":"Arena",
                                 "timezone":"America/Panama","startsAt":"%s","endsAt":"%s"}
                                """.formatted(title, categoryId, starts, starts.plusSeconds(7200))))
                .andExpect(status().isCreated()).andReturn();
        String id = parse(ev).at("/data/id").asText();
        int policyVersion = 0;
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/organizer/events/" + id + "/policy")
                        .header("Authorization", "Bearer " + organizerToken).header("If-Match", policyVersion)
                        .contentType(APPLICATION_JSON).content("""
                                {"exchangeEnabled":false,"exchangeDepreciationPct":10,"waitlistEnabled":false,
                                 "waitlistOfferMinutes":15,"tempReservationMinutes":10,
                                 "qrVisibilityHoursBefore":%d,"qrExpirationMinutes":60}
                                """.formatted(visibilityHours)))
                .andExpect(status().isOk());
        MvcResult tariff = mockMvc.perform(post("/organizer/events/" + id + "/ticket-types")
                        .header("Authorization", "Bearer " + organizerToken).contentType(APPLICATION_JSON)
                        .content("{\"name\":\"General\",\"price\":{\"amount\":\"20.00\",\"currency\":\"USD\"},\"totalQuantity\":50}"))
                .andExpect(status().isCreated()).andReturn();
        String tariffId = parse(tariff).at("/data/id").asText();
        mockMvc.perform(post("/organizer/events/" + id + "/publish")
                .header("Authorization", "Bearer " + organizerToken)).andExpect(status().isOk());
        // guarda el tariffId en un mapa simple por título
        jdbc.update("CREATE TEMP TABLE IF NOT EXISTS it_tariffs(title text, tariff text)");
        jdbc.update("INSERT INTO it_tariffs VALUES (?, ?)", title, tariffId);
        return id;
    }

    private String tariffOf(String title) {
        return jdbc.queryForObject("SELECT tariff FROM it_tariffs WHERE title=?", String.class, title);
    }

    @Test
    void t01_setup() throws Exception {
        organizerToken = register("ORGANIZER");
        buyerToken = register(null);
        // evento con ventana ya abierta (720h antes)
        eventId = setUpEvent("QRNow", Instant.now().plus(2, ChronoUnit.HOURS), 720);
        ticketId = buyTicket(eventId, tariffOf("QRNow"), buyerToken);
        // evento cuya ventana de QR aún NO abre (evento lejano, visibilidad corta)
        futureEventId = setUpEvent("QRFuture", Instant.now().plus(60, ChronoUnit.DAYS), 1);
        futureTicketId = buyTicket(futureEventId, tariffOf("QRFuture"), buyerToken);
    }

    @Test
    void t02_qr_before_window_is_forbidden() throws Exception {
        mockMvc.perform(get("/tickets/" + futureTicketId + "/qr").header("Authorization", "Bearer " + buyerToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("qr_not_yet_visible"));
    }

    @Test
    void t03_qr_returns_opaque_jws_within_window() throws Exception {
        mockMvc.perform(get("/tickets/" + ticketId + "/qr").header("Authorization", "Bearer " + buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.qrToken").isNotEmpty())
                .andExpect(jsonPath("$.data.expiresAt").isNotEmpty())
                .andExpect(jsonPath("$.data.refreshAfter").isNotEmpty());
    }

    @Test
    void t04_foreign_ticket_qr_is_404() throws Exception {
        String otherToken = register(null);
        mockMvc.perform(get("/tickets/" + ticketId + "/qr").header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void t05_checkin_requires_privileged_role() throws Exception {
        // un ATTENDEE (comprador) no tiene rol para el endpoint de check-in
        String token = mockMvc.perform(get("/tickets/" + ticketId + "/qr")
                        .header("Authorization", "Bearer " + buyerToken))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String qrToken = objectMapper.readTree(token).at("/data/qrToken").asText();
        mockMvc.perform(post("/events/" + eventId + "/check-ins").header("Authorization", "Bearer " + buyerToken)
                        .header("Idempotency-Key", UUID.randomUUID()).contentType(APPLICATION_JSON)
                        .content("{\"qrToken\":\"" + qrToken + "\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void t06_organizer_checkin_grants_then_reuse_is_conflict() throws Exception {
        MvcResult qr = mockMvc.perform(get("/tickets/" + ticketId + "/qr")
                        .header("Authorization", "Bearer " + buyerToken))
                .andExpect(status().isOk()).andReturn();
        String qrToken = parse(qr).at("/data/qrToken").asText();

        mockMvc.perform(post("/events/" + eventId + "/check-ins").header("Authorization", "Bearer " + organizerToken)
                        .header("Idempotency-Key", UUID.randomUUID()).contentType(APPLICATION_JSON)
                        .content("{\"qrToken\":\"" + qrToken + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.result").value("GRANTED"))
                .andExpect(jsonPath("$.data.attendeeName").value("QR Persona"))
                .andExpect(jsonPath("$.data.ticketTypeName").value("General"));

        // reuso ⇒ 409 already_used (Problem)
        mockMvc.perform(post("/events/" + eventId + "/check-ins").header("Authorization", "Bearer " + organizerToken)
                        .header("Idempotency-Key", UUID.randomUUID()).contentType(APPLICATION_JSON)
                        .content("{\"qrToken\":\"" + qrToken + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("already_used"));
    }

    @Test
    void t07_tampered_token_is_unprocessable() throws Exception {
        String token = "eyJhbGciOiJFUzI1NiJ9.eyJzdWIiOiJmYWtlIn0.INVALID_SIGNATURE_XXXXXXXXXXXXXX";
        mockMvc.perform(post("/events/" + eventId + "/check-ins").header("Authorization", "Bearer " + organizerToken)
                        .header("Idempotency-Key", UUID.randomUUID()).contentType(APPLICATION_JSON)
                        .content("{\"qrToken\":\"" + token + "\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("qr_invalid"));
    }

    @Test
    void t08_assign_and_remove_staff() throws Exception {
        String staffEmail = "qr-staff-" + UUID.randomUUID() + "@mail.com";
        mockMvc.perform(post("/auth/register").contentType(APPLICATION_JSON).content("""
                {"email":"%s","password":"S3gura!pass","fullName":"Staff","phone":"+50761234567"}
                """.formatted(staffEmail))).andExpect(status().isCreated());

        MvcResult assigned = mockMvc.perform(post("/organizer/events/" + eventId + "/staff")
                        .header("Authorization", "Bearer " + organizerToken).contentType(APPLICATION_JSON)
                        .content("{\"userEmail\":\"" + staffEmail + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.userId").isNotEmpty()).andReturn();
        String staffId = parse(assigned).at("/data/userId").asText();

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/organizer/events/" + eventId + "/staff/" + staffId)
                        .header("Authorization", "Bearer " + organizerToken))
                .andExpect(status().isNoContent());

        // email inexistente ⇒ 422
        mockMvc.perform(post("/organizer/events/" + eventId + "/staff")
                        .header("Authorization", "Bearer " + organizerToken).contentType(APPLICATION_JSON)
                        .content("{\"userEmail\":\"nadie@mail.com\"}"))
                .andExpect(status().isUnprocessableEntity());
    }
}
