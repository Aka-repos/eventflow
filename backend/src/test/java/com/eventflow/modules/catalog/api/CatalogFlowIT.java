package com.eventflow.modules.catalog.api;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * IT de contrato del Módulo 2 (catálogo + tarifas) contra PostgreSQL 17 real con Flyway V1–V9:
 * flujo organizador (crear→configurar→publicar), catálogo público (búsqueda/keyset/favoritos),
 * optimistic lock (If-Match), outbox (EventPublished/EventRescheduled) y seguridad por rol.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.MethodName.class)
class CatalogFlowIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    private static final Instant STARTS = Instant.now().plus(30, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
    private static final Instant ENDS = STARTS.plus(3, ChronoUnit.HOURS);

    private static String adminToken;
    private static String organizerToken;
    private static String attendeeToken;
    private static int categoryId;
    private static String eventId;
    private static String zoneId;
    private static String ticketTypeId;
    private static int eventVersion;

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbc;

    // ===== helpers =====

    private String registerAndLoginWithRole(String role) throws Exception {
        String email = "cat-" + UUID.randomUUID() + "@mail.com";
        mockMvc.perform(post("/auth/register").contentType(APPLICATION_JSON).content("""
                        {"email":"%s","password":"S3gura!pass","fullName":"Usuario Catalogo","phone":"+50761234567"}
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

    private String eventBody(String title) {
        return """
                {"title":"%s","description":"Gran concierto de prueba con orquesta",
                 "categoryId":%d,"venueName":"Estadio Nacional","address":"Calle 50, Ciudad de Panamá",
                 "latitude":8.9824,"longitude":-79.5199,"timezone":"America/Panama",
                 "startsAt":"%s","endsAt":"%s"}
                """.formatted(title, categoryId, STARTS, ENDS);
    }

    // ===== flujo =====

    @Test
    void t01_admin_creates_category_and_duplicate_is_conflict() throws Exception {
        adminToken = registerAndLoginWithRole("ADMIN");
        organizerToken = registerAndLoginWithRole("ORGANIZER");
        attendeeToken = registerAndLoginWithRole(null);

        MvcResult created = mockMvc.perform(post("/admin/categories")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(APPLICATION_JSON)
                        .content("{\"name\":\"Conciertos\",\"icon\":\"music\",\"active\":true}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("Conciertos"))
                .andReturn();
        categoryId = parse(created).at("/data/id").asInt();

        mockMvc.perform(post("/admin/categories")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(APPLICATION_JSON)
                        .content("{\"name\":\"Conciertos\",\"active\":true}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("category_name_taken"));
    }

    @Test
    void t02_organizer_creates_draft_event_with_default_policy() throws Exception {
        MvcResult created = mockMvc.perform(post("/organizer/events")
                        .header("Authorization", "Bearer " + organizerToken)
                        .contentType(APPLICATION_JSON)
                        .content(eventBody("Concierto Sinfónico de Prueba")))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.category.id").value(categoryId))
                .andExpect(jsonPath("$.data.policies.exchangeEnabled").value(false))
                .andExpect(jsonPath("$.data.policies.qrVisibilityHoursBefore").value(24))
                .andExpect(jsonPath("$.data.ticketTypes").isEmpty())
                .andReturn();
        eventId = parse(created).at("/data/id").asText();
    }

    @Test
    void t03_publish_without_tariffs_is_unprocessable() throws Exception {
        mockMvc.perform(post("/organizer/events/" + eventId + "/publish")
                        .header("Authorization", "Bearer " + organizerToken))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("event_not_publishable"));
    }

    @Test
    void t04_zone_and_ticket_type_creation() throws Exception {
        MvcResult zone = mockMvc.perform(post("/organizer/events/" + eventId + "/zones")
                        .header("Authorization", "Bearer " + organizerToken)
                        .contentType(APPLICATION_JSON)
                        .content("{\"name\":\"VIP\",\"capacity\":100}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("VIP"))
                .andReturn();
        zoneId = parse(zone).at("/data/id").asText();

        // zona duplicada = 422 (contrato createZone no declara 409)
        mockMvc.perform(post("/organizer/events/" + eventId + "/zones")
                        .header("Authorization", "Bearer " + organizerToken)
                        .contentType(APPLICATION_JSON)
                        .content("{\"name\":\"vip\",\"capacity\":50}"))
                .andExpect(status().isUnprocessableEntity());

        MvcResult tariff = mockMvc.perform(post("/organizer/events/" + eventId + "/ticket-types")
                        .header("Authorization", "Bearer " + organizerToken)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"name":"Entrada VIP","description":"Zona VIP",
                                 "price":{"amount":"75.00","currency":"USD"},
                                 "zoneId":"%s","totalQuantity":100}
                                """.formatted(zoneId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.price.amount").value("75.00"))
                .andExpect(jsonPath("$.data.zoneName").value("VIP"))
                .andExpect(jsonPath("$.data.available").value(true))
                .andReturn();
        ticketTypeId = parse(tariff).at("/data/id").asText();

        // zoneId ajeno = 422 semántico
        mockMvc.perform(post("/organizer/events/" + eventId + "/ticket-types")
                        .header("Authorization", "Bearer " + organizerToken)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"name":"Otra","price":{"amount":"10.00","currency":"USD"},
                                 "zoneId":"%s","totalQuantity":10}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[?(@.field=='zoneId')]").exists());
    }

    @Test
    void t05_publish_emits_outbox_event_and_is_not_repeatable() throws Exception {
        mockMvc.perform(post("/organizer/events/" + eventId + "/publish")
                        .header("Authorization", "Bearer " + organizerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"));

        Integer outboxCount = jdbc.queryForObject(
                "SELECT count(*) FROM ops.outbox_events WHERE event_type = 'EventPublished' AND aggregate_id = ?::uuid",
                Integer.class, eventId);
        assertThat(outboxCount).isEqualTo(1);

        mockMvc.perform(post("/organizer/events/" + eventId + "/publish")
                        .header("Authorization", "Bearer " + organizerToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("event_not_draft"));
    }

    @Test
    void t06_public_search_finds_published_event() throws Exception {
        mockMvc.perform(get("/events").param("q", "sinfónico").param("categoryId", String.valueOf(categoryId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(eventId))
                .andExpect(jsonPath("$.data[0].priceFrom.amount").value("75.00"))
                .andExpect(jsonPath("$.data[0].isFavorite").doesNotExist())
                .andExpect(jsonPath("$.meta.hasNext").value(false));

        // búsqueda sin match
        mockMvc.perform(get("/events").param("q", "inexistentexyz"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());

        // filtro geográfico: cerca del venue (Panamá) sí, lejos (Bogotá) no
        mockMvc.perform(get("/events").param("nearLat", "8.98").param("nearLng", "-79.52").param("radiusKm", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(eventId));
        mockMvc.perform(get("/events").param("nearLat", "4.71").param("nearLng", "-74.07").param("radiusKm", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void t07_public_detail_contains_tariffs_zones_and_public_policy() throws Exception {
        mockMvc.perform(get("/events/" + eventId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ticketTypes[0].name").value("Entrada VIP"))
                .andExpect(jsonPath("$.data.zones[0].name").value("VIP"))
                .andExpect(jsonPath("$.data.policies.waitlistEnabled").value(false))
                .andExpect(jsonPath("$.data.policies.refundPct").doesNotExist())
                .andExpect(jsonPath("$.data.waitlistOpen").value(false))
                .andExpect(jsonPath("$.data.organizer.name").value("Usuario Catalogo"))
                .andExpect(jsonPath("$.data.parkings").isEmpty());
    }

    @Test
    void t08_favorites_roundtrip_is_idempotent() throws Exception {
        mockMvc.perform(put("/me/favorites/" + eventId).header("Authorization", "Bearer " + attendeeToken))
                .andExpect(status().isNoContent());
        mockMvc.perform(put("/me/favorites/" + eventId).header("Authorization", "Bearer " + attendeeToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/me/favorites").header("Authorization", "Bearer " + attendeeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(eventId))
                .andExpect(jsonPath("$.data[0].isFavorite").value(true));

        mockMvc.perform(get("/events/" + eventId).header("Authorization", "Bearer " + attendeeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isFavorite").value(true));

        mockMvc.perform(put("/me/favorites/" + UUID.randomUUID())
                        .header("Authorization", "Bearer " + attendeeToken))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/me/favorites/" + eventId).header("Authorization", "Bearer " + attendeeToken))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/me/favorites/" + eventId).header("Authorization", "Bearer " + attendeeToken))
                .andExpect(status().isNoContent());
    }

    @Test
    void t09_patch_with_stale_if_match_returns_conflict_version() throws Exception {
        MvcResult result = mockMvc.perform(patch("/organizer/events/" + eventId)
                        .header("Authorization", "Bearer " + organizerToken)
                        .header("If-Match", "99")
                        .contentType(APPLICATION_JSON)
                        .content("{\"description\":\"Nueva descripción\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("version_conflict"))
                .andExpect(jsonPath("$.conflictVersion").isNumber())
                .andReturn();
        eventVersion = parse(result).at("/conflictVersion").asInt();

        // sin If-Match = 400 malformed_request
        mockMvc.perform(patch("/organizer/events/" + eventId)
                        .header("Authorization", "Bearer " + organizerToken)
                        .contentType(APPLICATION_JSON)
                        .content("{\"description\":\"x\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void t10_patch_unsafe_field_after_publish_is_conflict() throws Exception {
        mockMvc.perform(patch("/organizer/events/" + eventId)
                        .header("Authorization", "Bearer " + organizerToken)
                        .header("If-Match", String.valueOf(eventVersion))
                        .contentType(APPLICATION_JSON)
                        .content("{\"title\":\"Título Cambiado Ilegalmente\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("event_not_published"));
    }

    @Test
    void t11_patch_dates_after_publish_reschedules_and_emits_event() throws Exception {
        Instant newStarts = STARTS.plus(1, ChronoUnit.DAYS);
        Instant newEnds = ENDS.plus(1, ChronoUnit.DAYS);
        MvcResult result = mockMvc.perform(patch("/organizer/events/" + eventId)
                        .header("Authorization", "Bearer " + organizerToken)
                        .header("If-Match", String.valueOf(eventVersion))
                        .contentType(APPLICATION_JSON)
                        .content("{\"startsAt\":\"%s\",\"endsAt\":\"%s\",\"description\":\"Reprogramado\"}"
                                .formatted(newStarts, newEnds)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.description").value("Reprogramado"))
                .andReturn();
        eventVersion = parse(result).at("/data").hasNonNull("id") ? eventVersion + 1 : eventVersion;

        Integer rescheduled = jdbc.queryForObject(
                "SELECT count(*) FROM ops.outbox_events WHERE event_type = 'EventRescheduled' AND aggregate_id = ?::uuid",
                Integer.class, eventId);
        assertThat(rescheduled).isEqualTo(1);
    }

    @Test
    void t12_policy_get_and_replace_with_optimistic_lock() throws Exception {
        MvcResult policy = mockMvc.perform(get("/organizer/events/" + eventId + "/policy")
                        .header("Authorization", "Bearer " + organizerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.eventId").value(eventId))
                .andExpect(jsonPath("$.data.refundWindowEndsAt").doesNotExist())
                .andReturn();
        int policyVersion = parse(policy).at("/data/version").asInt();

        String policyBody = """
                {"refundWindowEndsAt":"%s","exchangeEnabled":true,"exchangeDepreciationPct":15,
                 "waitlistEnabled":true,"waitlistOfferMinutes":20,"tempReservationMinutes":10,
                 "qrVisibilityHoursBefore":48,"qrExpirationMinutes":30,
                 "cancellationPolicy":"Sin devolución después de la ventana"}
                """.formatted(STARTS.minus(2, ChronoUnit.DAYS));

        mockMvc.perform(put("/organizer/events/" + eventId + "/policy")
                        .header("Authorization", "Bearer " + organizerToken)
                        .header("If-Match", String.valueOf(policyVersion))
                        .contentType(APPLICATION_JSON)
                        .content(policyBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.exchangeEnabled").value(true))
                .andExpect(jsonPath("$.data.version").value(policyVersion + 1));

        mockMvc.perform(put("/organizer/events/" + eventId + "/policy")
                        .header("Authorization", "Bearer " + organizerToken)
                        .header("If-Match", String.valueOf(policyVersion))
                        .contentType(APPLICATION_JSON)
                        .content(policyBody))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("version_conflict"))
                .andExpect(jsonPath("$.conflictVersion").value(policyVersion + 1));
    }

    @Test
    void t13_zone_with_tariffs_cannot_be_deleted() throws Exception {
        mockMvc.perform(delete("/organizer/events/" + eventId + "/zones/" + zoneId)
                        .header("Authorization", "Bearer " + organizerToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("zone_in_use"));
    }

    @Test
    void t14_ticket_type_update_delete_and_then_zone_delete() throws Exception {
        // PATCH tarifa con If-Match
        mockMvc.perform(patch("/organizer/events/" + eventId + "/ticket-types/" + ticketTypeId)
                        .header("Authorization", "Bearer " + organizerToken)
                        .header("If-Match", "0")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"name":"Entrada VIP Plus","price":{"amount":"80.00","currency":"USD"},
                                 "zoneId":"%s","totalQuantity":120}
                                """.formatted(zoneId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Entrada VIP Plus"))
                .andExpect(jsonPath("$.data.price.amount").value("80.00"));

        mockMvc.perform(delete("/organizer/events/" + eventId + "/ticket-types/" + ticketTypeId)
                        .header("Authorization", "Bearer " + organizerToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(delete("/organizer/events/" + eventId + "/zones/" + zoneId)
                        .header("Authorization", "Bearer " + organizerToken))
                .andExpect(status().isNoContent());
    }

    @Test
    void t15_category_in_use_cannot_be_deleted() throws Exception {
        mockMvc.perform(delete("/admin/categories/" + categoryId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("category_in_use"));
    }

    @Test
    void t16_role_security_matrix() throws Exception {
        // attendee no es organizador
        mockMvc.perform(post("/organizer/events")
                        .header("Authorization", "Bearer " + attendeeToken)
                        .contentType(APPLICATION_JSON)
                        .content(eventBody("Evento De Atacante")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("forbidden"));
        // organizador no es admin
        mockMvc.perform(post("/admin/categories")
                        .header("Authorization", "Bearer " + organizerToken)
                        .contentType(APPLICATION_JSON)
                        .content("{\"name\":\"Hacking\",\"active\":true}"))
                .andExpect(status().isForbidden());
        // sin token
        mockMvc.perform(get("/me/favorites")).andExpect(status().isUnauthorized());
        // organizador ajeno no ve el evento (404 anti-enumeración)
        String otherOrganizer = registerAndLoginWithRole("ORGANIZER");
        mockMvc.perform(post("/organizer/events/" + eventId + "/publish")
                        .header("Authorization", "Bearer " + otherOrganizer))
                .andExpect(status().isNotFound());
    }

    @Test
    void t17_delete_rules_and_draft_invisibility() throws Exception {
        // publicado no se elimina
        mockMvc.perform(delete("/organizer/events/" + eventId)
                        .header("Authorization", "Bearer " + organizerToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("event_not_draft"));

        // draft: invisible al público y eliminable
        MvcResult draft = mockMvc.perform(post("/organizer/events")
                        .header("Authorization", "Bearer " + organizerToken)
                        .contentType(APPLICATION_JSON)
                        .content(eventBody("Borrador Temporal De Prueba")))
                .andExpect(status().isCreated()).andReturn();
        String draftId = parse(draft).at("/data/id").asText();

        mockMvc.perform(get("/events/" + draftId)).andExpect(status().isNotFound());
        mockMvc.perform(delete("/organizer/events/" + draftId)
                        .header("Authorization", "Bearer " + organizerToken))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/events").param("q", "borrador temporal"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void t18_keyset_pagination_with_opaque_cursor() throws Exception {
        // dos eventos publicados adicionales con horarios distintos
        for (int i = 1; i <= 2; i++) {
            MvcResult created = mockMvc.perform(post("/organizer/events")
                            .header("Authorization", "Bearer " + organizerToken)
                            .contentType(APPLICATION_JSON)
                            .content("""
                                    {"title":"Evento Paginado %d","description":"paginación",
                                     "categoryId":%d,"venueName":"Venue %d","timezone":"America/Panama",
                                     "startsAt":"%s","endsAt":"%s"}
                                    """.formatted(i, categoryId, i,
                                    STARTS.plus(10L + i, ChronoUnit.DAYS), ENDS.plus(10L + i, ChronoUnit.DAYS))))
                    .andExpect(status().isCreated()).andReturn();
            String id = parse(created).at("/data/id").asText();
            mockMvc.perform(post("/organizer/events/" + id + "/ticket-types")
                            .header("Authorization", "Bearer " + organizerToken)
                            .contentType(APPLICATION_JSON)
                            .content("{\"name\":\"General\",\"price\":{\"amount\":\"10.00\",\"currency\":\"USD\"},\"totalQuantity\":50}"))
                    .andExpect(status().isCreated());
            mockMvc.perform(post("/organizer/events/" + id + "/publish")
                            .header("Authorization", "Bearer " + organizerToken))
                    .andExpect(status().isOk());
        }

        MvcResult page1 = mockMvc.perform(get("/events").param("limit", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.meta.hasNext").value(true))
                .andExpect(jsonPath("$.meta.nextCursor").isNotEmpty())
                .andReturn();
        String cursor = parse(page1).at("/meta/nextCursor").asText();
        String firstId = parse(page1).at("/data/0/id").asText();

        MvcResult page2 = mockMvc.perform(get("/events").param("limit", "2").param("cursor", cursor))
                .andExpect(status().isOk()).andReturn();
        assertThat(parse(page2).at("/data/0/id").asText()).isNotEqualTo(firstId);

        // cursor emitido con otro orden = 400 malformed_request (api/07 §1)
        mockMvc.perform(get("/events").param("sort", "-startsAt").param("cursor", cursor))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("malformed_request"));

        // organizador: su lista pagina igual
        mockMvc.perform(get("/organizer/events").param("limit", "2")
                        .header("Authorization", "Bearer " + organizerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.hasNext").value(true));
    }
}
