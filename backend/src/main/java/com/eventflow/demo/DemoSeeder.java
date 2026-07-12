package com.eventflow.demo;

import com.eventflow.modules.catalog.application.CreateCategoryUseCase;
import com.eventflow.modules.catalog.application.CreateEventUseCase;
import com.eventflow.modules.catalog.application.CreateSponsorUseCase;
import com.eventflow.modules.catalog.application.CreateZoneUseCase;
import com.eventflow.modules.catalog.application.PublishEventUseCase;
import com.eventflow.modules.catalog.application.UpdateEventPolicyUseCase;
import com.eventflow.modules.catalog.application.command.CreateEventCommand;
import com.eventflow.modules.catalog.application.command.UpdatePolicyCommand;
import com.eventflow.modules.catalog.domain.EventZone;
import com.eventflow.modules.identity.application.RegisterUserUseCase;
import com.eventflow.modules.identity.application.command.RegisterUserCommand;
import com.eventflow.modules.ticketing.application.CreateTicketTypeUseCase;
import com.eventflow.modules.ticketing.application.command.TicketTypeCommand;
import com.eventflow.shared.domain.Money;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Demo Seed oficial (SOLO perfil "demo"; jamás en prod). Idempotente: cada entidad se busca por su
 * clave natural (email/nombre/título) y solo se crea si falta — re-ejecutar no duplica nada.
 *
 * Crea los datos a través de los CASOS DE USO reales (mismas invariantes y outbox que producción).
 * Únicas excepciones por SQL directo, porque el contrato v1 no expone la operación:
 * promoción de roles (llega en módulo 10) y vinculación sponsor↔evento (mejora propuesta en
 * docs/reports/02 §7.2). No toca migraciones Flyway.
 */
@Component
@Profile("demo")
public class DemoSeeder implements ApplicationRunner {

    public static final String DEMO_PASSWORD = "Demo1234!";
    private static final String ADMIN_EMAIL = "demo-admin@eventflow.dev";
    private static final String ORGANIZER_EMAIL = "demo-organizer@eventflow.dev";
    private static final String ATTENDEE_EMAIL = "demo-user@eventflow.dev";

    private static final Logger log = LoggerFactory.getLogger(DemoSeeder.class);

    private final RegisterUserUseCase registerUser;
    private final CreateCategoryUseCase createCategory;
    private final CreateSponsorUseCase createSponsor;
    private final CreateEventUseCase createEvent;
    private final CreateZoneUseCase createZone;
    private final CreateTicketTypeUseCase createTicketType;
    private final UpdateEventPolicyUseCase updatePolicy;
    private final PublishEventUseCase publishEvent;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public DemoSeeder(RegisterUserUseCase registerUser, CreateCategoryUseCase createCategory,
                      CreateSponsorUseCase createSponsor, CreateEventUseCase createEvent,
                      CreateZoneUseCase createZone, CreateTicketTypeUseCase createTicketType,
                      UpdateEventPolicyUseCase updatePolicy, PublishEventUseCase publishEvent,
                      JdbcTemplate jdbc, Clock clock) {
        this.registerUser = registerUser;
        this.createCategory = createCategory;
        this.createSponsor = createSponsor;
        this.createEvent = createEvent;
        this.createZone = createZone;
        this.createTicketType = createTicketType;
        this.updatePolicy = updatePolicy;
        this.publishEvent = publishEvent;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        log.info("demo_seed_start");
        UUID adminId = ensureUser(ADMIN_EMAIL, "Ada Admin Demo", "ADMIN");
        UUID organizerId = ensureUser(ORGANIZER_EMAIL, "Olga Organizadora Demo", "ORGANIZER");
        UUID attendeeId = ensureUser(ATTENDEE_EMAIL, "Andrés Asistente Demo", null);

        short conciertos = ensureCategory("Conciertos", "music");
        short deportes = ensureCategory("Deportes", "sports");
        short teatro = ensureCategory("Teatro", "theater");
        short tecnologia = ensureCategory("Tecnología", "tech");

        UUID acme = ensureSponsor("Acme Bebidas", "https://logo.dev/acme.png", "https://acme.example");
        UUID globalBank = ensureSponsor("Global Bank", null, "https://globalbank.example");
        UUID nimbus = ensureSponsor("Nimbus Telecom", "https://logo.dev/nimbus.png", null);

        Instant base = clock.instant().truncatedTo(ChronoUnit.HOURS);

        UUID festival = ensurePublishedEvent(organizerId, conciertos, "Festival Ritmo Urbano",
                "Tres escenarios, veinte artistas y food trucks toda la noche.",
                "Parque Omar", "Vía Porras, Ciudad de Panamá", 9.0031, -79.5063,
                base.plus(14, ChronoUnit.DAYS), 6,
                List.of(new ZoneSpec("VIP", 300), new ZoneSpec("General", 2500)),
                List.of(new TariffSpec("Entrada VIP", "VIP", "120.00", 300),
                        new TariffSpec("Entrada General", "General", "45.00", 2500)),
                policy(true, 10, true, base.plus(12, ChronoUnit.DAYS)));

        UUID clasico = ensurePublishedEvent(organizerId, deportes, "Clásico Nacional de Fútbol",
                "La final del torneo clausura con las dos aficiones más grandes del país.",
                "Estadio Rommel Fernández", "Vía Israel, Ciudad de Panamá", 9.0136, -79.4653,
                base.plus(21, ChronoUnit.DAYS), 3,
                List.of(new ZoneSpec("Tribuna Norte", 8000), new ZoneSpec("Palco", 500)),
                List.of(new TariffSpec("Tribuna", "Tribuna Norte", "18.00", 8000),
                        new TariffSpec("Palco Premium", "Palco", "95.00", 500)),
                policy(false, 10, true, null));

        ensurePublishedEvent(organizerId, teatro, "La Casa de Bernarda Alba",
                "Temporada limitada del clásico de Lorca con elenco nacional.",
                "Teatro Nacional", "Casco Antiguo, Ciudad de Panamá", 8.9527, -79.5357,
                base.plus(7, ChronoUnit.DAYS), 2,
                List.of(),
                List.of(new TariffSpec("Butaca", null, "30.00", 400)),
                policy(false, 10, false, null));

        ensurePublishedEvent(organizerId, tecnologia, "DevConf Panamá 2026",
                "Conferencia de desarrollo de software: IA aplicada, cloud y plataforma.",
                "Centro de Convenciones Amador", "Calzada de Amador", 8.9227, -79.5432,
                base.plus(30, ChronoUnit.DAYS), 10,
                List.of(new ZoneSpec("Sala Principal", 1200)),
                List.of(new TariffSpec("Pase Completo", "Sala Principal", "150.00", 1000),
                        new TariffSpec("Pase Estudiante", "Sala Principal", "60.00", 200)),
                policy(true, 15, false, base.plus(28, ChronoUnit.DAYS)));

        ensurePublishedEvent(organizerId, conciertos, "Noche Sinfónica bajo las Estrellas",
                "La orquesta sinfónica interpreta bandas sonoras de cine al aire libre.",
                "Cinta Costera", "Av. Balboa", 8.9714, -79.5342,
                base.plus(45, ChronoUnit.DAYS), 4,
                List.of(),
                List.of(new TariffSpec("Entrada Única", null, "25.00", 3000)),
                policy(false, 10, true, null));

        linkSponsor(acme, festival);
        linkSponsor(nimbus, festival);
        linkSponsor(globalBank, clasico);

        log.info("demo_seed_done admin={} organizer={} attendee={}", adminId, organizerId, attendeeId);
    }

    // ===== usuarios =====

    private UUID ensureUser(String email, String fullName, String role) {
        UUID id = findUuid("SELECT id FROM identity.users WHERE email = ?", email);
        if (id == null) {
            registerUser.execute(new RegisterUserCommand(email, DEMO_PASSWORD, fullName, "+50760000000"));
            id = findUuid("SELECT id FROM identity.users WHERE email = ?", email);
            log.info("demo_seed user creado email={}", email);
        }
        if (role != null) {
            // Promoción de rol: sin endpoint hasta el módulo 10 (admin) — SQL idempotente
            jdbc.update("""
                    INSERT INTO identity.user_roles (user_id, role_id)
                    SELECT ?, r.id FROM identity.roles r WHERE r.code = ?
                    ON CONFLICT DO NOTHING
                    """, id, role);
        }
        return id;
    }

    // ===== catálogo =====

    private short ensureCategory(String name, String icon) {
        Short id = jdbc.query("SELECT id FROM catalog.categories WHERE name = ?",
                rs -> rs.next() ? rs.getShort(1) : null, name);
        if (id != null) {
            return id;
        }
        short created = createCategory.execute(name, icon, true).getId();
        log.info("demo_seed categoria creada name={}", name);
        return created;
    }

    private UUID ensureSponsor(String name, String logoUrl, String website) {
        UUID id = findUuid("SELECT id FROM catalog.sponsors WHERE name = ?", name);
        if (id != null) {
            return id;
        }
        UUID created = createSponsor.execute(name, logoUrl, website).getId();
        log.info("demo_seed sponsor creado name={}", name);
        return created;
    }

    private UUID ensurePublishedEvent(UUID organizerId, short categoryId, String title, String description,
                                      String venue, String address, double lat, double lng,
                                      Instant startsAt, int durationHours,
                                      List<ZoneSpec> zones, List<TariffSpec> tariffs,
                                      UpdatePolicyCommand policyTemplate) {
        UUID existing = findUuid(
                "SELECT id FROM catalog.events WHERE organizer_id = ? AND title = ? AND deleted_at IS NULL",
                organizerId, title);
        if (existing != null) {
            return existing;
        }
        UUID eventId = createEvent.execute(new CreateEventCommand(organizerId, title, description,
                categoryId, venue, address, lat, lng, "America/Panama",
                startsAt, startsAt.plus(durationHours, ChronoUnit.HOURS))).event().getId();

        java.util.Map<String, UUID> zoneIds = new java.util.HashMap<>();
        for (ZoneSpec zone : zones) {
            EventZone created = createZone.execute(organizerId, eventId, zone.name(), zone.capacity());
            zoneIds.put(zone.name(), created.getId());
        }
        for (TariffSpec tariff : tariffs) {
            createTicketType.execute(new TicketTypeCommand(organizerId, eventId, tariff.name(), null,
                    Money.of(tariff.price(), "USD"), tariff.zoneName() == null ? null : zoneIds.get(tariff.zoneName()),
                    tariff.quantity(), null, null));
        }
        // La política nace con defaults (versión 0) al crear el evento; aquí se personaliza
        updatePolicy.execute(new UpdatePolicyCommand(organizerId, eventId, 0,
                policyTemplate.refundWindowEndsAt(), policyTemplate.exchangeEnabled(),
                policyTemplate.exchangeDepreciationPct(), policyTemplate.exchangeListingDeadline(),
                policyTemplate.waitlistEnabled(), policyTemplate.waitlistOfferMinutes(),
                policyTemplate.tempReservationMinutes(), policyTemplate.qrVisibilityHoursBefore(),
                policyTemplate.qrExpirationMinutes(), policyTemplate.cancellationPolicy(), Map.of()));
        publishEvent.execute(organizerId, eventId);
        log.info("demo_seed evento publicado title={}", title);
        return eventId;
    }

    private UpdatePolicyCommand policy(boolean exchange, int depreciationPct, boolean waitlist,
                                       Instant exchangeDeadline) {
        return new UpdatePolicyCommand(null, null, 0, null, exchange, depreciationPct, exchangeDeadline,
                waitlist, 15, 10, 24, 60, "Reembolso del 100% dentro de la ventana configurada.", Map.of());
    }

    private void linkSponsor(UUID sponsorId, UUID eventId) {
        // Vinculación sin endpoint en v1 (propuesta docs/reports/02 §7.2) — SQL idempotente demo-only
        jdbc.update("INSERT INTO catalog.sponsor_events (sponsor_id, event_id) VALUES (?, ?) "
                + "ON CONFLICT DO NOTHING", sponsorId, eventId);
    }

    private UUID findUuid(String sql, Object... params) {
        return jdbc.query(sql, rs -> rs.next() ? rs.getObject(1, UUID.class) : null, params);
    }

    private record ZoneSpec(String name, int capacity) {
    }

    private record TariffSpec(String name, String zoneName, String price, int quantity) {
    }
}
