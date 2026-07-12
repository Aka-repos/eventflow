package com.eventflow.demo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El Demo Seed corre al arrancar con el perfil demo (PostgreSQL 17 real + Flyway) y es
 * IDEMPOTENTE: re-ejecutarlo no duplica usuarios, categorías, sponsors, eventos ni tarifas.
 */
@Testcontainers
@SpringBootTest
@ActiveProfiles("demo")
class DemoSeedIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @Autowired DemoSeeder seeder;
    @Autowired JdbcTemplate jdbc;

    private int count(String sql) {
        Integer n = jdbc.queryForObject(sql, Integer.class);
        return n == null ? 0 : n;
    }

    @Test
    void seed_populates_catalog_and_is_idempotent() {
        // El ApplicationRunner ya corrió en el arranque del contexto
        assertThat(count("SELECT count(*) FROM identity.users WHERE email LIKE 'demo-%@eventflow.dev'"))
                .isEqualTo(3);
        assertThat(count("SELECT count(*) FROM catalog.categories")).isEqualTo(4);
        assertThat(count("SELECT count(*) FROM catalog.sponsors")).isEqualTo(3);
        assertThat(count("SELECT count(*) FROM catalog.events WHERE status = 'PUBLISHED'")).isEqualTo(5);
        assertThat(count("SELECT count(*) FROM ticketing.ticket_types")).isEqualTo(8);
        assertThat(count("SELECT count(*) FROM catalog.event_zones")).isEqualTo(5);
        assertThat(count("SELECT count(*) FROM catalog.sponsor_events")).isEqualTo(3);
        assertThat(count("SELECT count(*) FROM catalog.event_policies")).isEqualTo(5);
        // roles: admin y organizador promovidos (ATTENDEE base + rol extra)
        assertThat(count("""
                SELECT count(*) FROM identity.user_roles ur
                JOIN identity.users u ON u.id = ur.user_id
                WHERE u.email LIKE 'demo-%@eventflow.dev'
                """)).isEqualTo(5);
        // outbox: un EventPublished por evento publicado (misma TX que el publish)
        assertThat(count("SELECT count(*) FROM ops.outbox_events WHERE event_type = 'EventPublished'"))
                .isEqualTo(5);

        // Segunda ejecución = cero duplicados
        seeder.run(null);

        assertThat(count("SELECT count(*) FROM identity.users WHERE email LIKE 'demo-%@eventflow.dev'"))
                .isEqualTo(3);
        assertThat(count("SELECT count(*) FROM catalog.categories")).isEqualTo(4);
        assertThat(count("SELECT count(*) FROM catalog.sponsors")).isEqualTo(3);
        assertThat(count("SELECT count(*) FROM catalog.events")).isEqualTo(5);
        assertThat(count("SELECT count(*) FROM ticketing.ticket_types")).isEqualTo(8);
        assertThat(count("SELECT count(*) FROM catalog.sponsor_events")).isEqualTo(3);
        assertThat(count("SELECT count(*) FROM ops.outbox_events WHERE event_type = 'EventPublished'"))
                .isEqualTo(5);
    }
}
