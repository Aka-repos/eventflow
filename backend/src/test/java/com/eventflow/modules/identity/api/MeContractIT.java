package com.eventflow.modules.identity.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
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

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * IT de contrato de PUT /me (updateProfile) contra PostgreSQL 17 real con Flyway V1–V9.
 * Cubre: actualización 200, limpieza de phone, validación 422 (E.164 / nombre vacío) y 401 sin token.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class MeContractIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    private String registerAndGetToken() throws Exception {
        String email = "me-" + UUID.randomUUID() + "@mail.com";
        MvcResult res = mockMvc.perform(post("/auth/register").contentType(APPLICATION_JSON).content("""
                {"email":"%s","password":"S3gura!pass","fullName":"Ana Prueba","phone":"+50761234567"}
                """.formatted(email))).andExpect(status().isCreated()).andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).at("/data/accessToken").asText();
    }

    @Test
    void put_me_updates_name_and_phone() throws Exception {
        String token = registerAndGetToken();
        mockMvc.perform(put("/me").header("Authorization", "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content("{\"fullName\":\"Ana María Vega\",\"phone\":\"+50769999999\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fullName").value("Ana María Vega"))
                .andExpect(jsonPath("$.data.phone").value("+50769999999"))
                .andExpect(jsonPath("$.data.email").isNotEmpty())
                .andExpect(jsonPath("$.data.roles[0]").value("ATTENDEE"));
    }

    @Test
    void put_me_clears_phone_when_omitted() throws Exception {
        String token = registerAndGetToken();
        mockMvc.perform(put("/me").header("Authorization", "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content("{\"fullName\":\"Solo Nombre\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fullName").value("Solo Nombre"))
                .andExpect(jsonPath("$.data.phone").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void put_me_rejects_invalid_phone_with_422() throws Exception {
        String token = registerAndGetToken();
        mockMvc.perform(put("/me").header("Authorization", "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content("{\"fullName\":\"Ana\",\"phone\":\"12345\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("validation_error"));
    }

    @Test
    void put_me_rejects_blank_name_with_422() throws Exception {
        String token = registerAndGetToken();
        mockMvc.perform(put("/me").header("Authorization", "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content("{\"fullName\":\"\"}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void put_me_without_token_is_401() throws Exception {
        mockMvc.perform(put("/me").contentType(APPLICATION_JSON)
                        .content("{\"fullName\":\"Ana\"}"))
                .andExpect(status().isUnauthorized());
    }
}
