package com.eventflow.modules.identity.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * IT de contrato del módulo identity contra PostgreSQL 17 real con las migraciones Flyway V1–V9
 * (jamás H2 — engineering/04 §3). Cubre: registro, duplicado, login, credenciales, validación 422,
 * rotación de refresh, detección de reuso (revoca familia) y logout.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.MethodName.class)
class AuthFlowIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    private String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@mail.com";
    }

    private String registerBody(String email) {
        return """
                {"email":"%s","password":"S3gura!pass","fullName":"Ana Prueba","phone":"+50761234567"}
                """.formatted(email);
    }

    private JsonNode registerAndParse(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/register").contentType(APPLICATION_JSON)
                        .content(registerBody(email)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
    }

    @Test
    void t01_register_returns_201_with_tokens_and_profile() throws Exception {
        String email = uniqueEmail();
        mockMvc.perform(post("/auth/register").contentType(APPLICATION_JSON).content(registerBody(email)))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(header().exists("X-Correlation-ID"))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.accessTokenExpiresIn").value(900))
                .andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.data.user.email").value(email))
                .andExpect(jsonPath("$.data.user.roles[0]").value("ATTENDEE"));
    }

    @Test
    void t02_duplicate_email_returns_409_problem() throws Exception {
        String email = uniqueEmail();
        registerAndParse(email);
        mockMvc.perform(post("/auth/register").contentType(APPLICATION_JSON).content(registerBody(email)))
                .andExpect(status().isConflict())
                .andExpect(header().string("Content-Type", "application/problem+json"))
                .andExpect(jsonPath("$.code").value("email_already_registered"))
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                .andExpect(jsonPath("$.instance").value("/auth/register"));
    }

    @Test
    void t03_duplicate_email_is_case_insensitive_thanks_to_citext() throws Exception {
        String email = uniqueEmail();
        registerAndParse(email);
        mockMvc.perform(post("/auth/register").contentType(APPLICATION_JSON)
                        .content(registerBody(email.toUpperCase())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("email_already_registered"));
    }

    @Test
    void t04_validation_failure_returns_422_with_field_errors() throws Exception {
        mockMvc.perform(post("/auth/register").contentType(APPLICATION_JSON)
                        .content("""
                                {"email":"no-es-email","password":"corta","fullName":""}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("validation_error"))
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.errors[?(@.field=='email')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field=='password')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field=='fullName')]").exists());
    }

    @Test
    void t05_login_with_wrong_password_returns_401_invalid_credentials() throws Exception {
        String email = uniqueEmail();
        registerAndParse(email);
        mockMvc.perform(post("/auth/login").contentType(APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"incorrecta1\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("invalid_credentials"));
    }

    @Test
    void t06_login_returns_tokens_and_access_token_grants_access() throws Exception {
        String email = uniqueEmail();
        registerAndParse(email);
        MvcResult login = mockMvc.perform(post("/auth/login").contentType(APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"S3gura!pass\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String accessToken = objectMapper.readTree(login.getResponse().getContentAsString())
                .at("/data/accessToken").asText();

        // Un endpoint protegido inexistente con token válido debe dar 404 (autenticado), no 401
        mockMvc.perform(get("/tickets").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNotFound());
        // Y sin token, 401 con problem
        mockMvc.perform(get("/tickets"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("unauthorized"));
    }

    @Test
    void t07_refresh_rotates_token_and_old_one_stops_working() throws Exception {
        JsonNode data = registerAndParse(uniqueEmail());
        String firstRefresh = data.get("refreshToken").asText();

        // Rotación: el refresh devuelve tokens nuevos
        MvcResult rotated = mockMvc.perform(post("/auth/refresh").contentType(APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + firstRefresh + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
                .andReturn();
        String secondRefresh = objectMapper.readTree(rotated.getResponse().getContentAsString())
                .at("/data/refreshToken").asText();
        assertThat(secondRefresh).isNotEqualTo(firstRefresh);

        // Reuso del token YA rotado = robo detectado → 401 refresh_token_reused
        mockMvc.perform(post("/auth/refresh").contentType(APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + firstRefresh + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("refresh_token_reused"));

        // La familia completa quedó revocada: el sucesor tampoco sirve
        mockMvc.perform(post("/auth/refresh").contentType(APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + secondRefresh + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("token_invalid"));
    }

    @Test
    void t08_logout_revokes_refresh_token_idempotently() throws Exception {
        JsonNode data = registerAndParse(uniqueEmail());
        String accessToken = data.get("accessToken").asText();
        String refreshToken = data.get("refreshToken").asText();

        mockMvc.perform(post("/auth/logout").contentType(APPLICATION_JSON)
                        .header("Authorization", "Bearer " + accessToken)
                        .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(status().isNoContent());

        // El token revocado por logout no rota (y no es "reuso": no revoca familia)
        mockMvc.perform(post("/auth/refresh").contentType(APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("token_invalid"));

        // Logout repetido sigue siendo 204 (idempotente)
        mockMvc.perform(post("/auth/logout").contentType(APPLICATION_JSON)
                        .header("Authorization", "Bearer " + accessToken)
                        .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(status().isNoContent());
    }

    @Test
    void t09_logout_without_token_returns_401() throws Exception {
        mockMvc.perform(post("/auth/logout").contentType(APPLICATION_JSON)
                        .content("{\"refreshToken\":\"x\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void t10_malformed_json_returns_400() throws Exception {
        mockMvc.perform(post("/auth/login").contentType(APPLICATION_JSON).content("{no-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("malformed_request"));
    }
}
