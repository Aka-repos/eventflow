package com.eventflow.modules.catalog.domain;

import com.eventflow.modules.catalog.domain.exception.EventFieldNotEditableException;
import com.eventflow.modules.catalog.domain.exception.EventNotDraftException;
import com.eventflow.modules.catalog.domain.exception.EventNotPublishableException;
import com.eventflow.shared.domain.Uuids;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Agregado Event (catalog). Estados y ediciones según design/04 §3; el estado solo cambia
 * por métodos de negocio. Soft delete (ADR-16). search_vector es columna generada por la BD.
 */
@Entity
@Table(name = "events", schema = "catalog")
@SQLRestriction("deleted_at IS NULL")
public class Event {

    @Id
    private UUID id;

    @Column(name = "organizer_id", nullable = false, updatable = false)
    private UUID organizerId;

    @Column(name = "category_id", nullable = false)
    private short categoryId;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String description;

    @Column(name = "venue_name", nullable = false)
    private String venueName;

    private String address;

    private BigDecimal latitude;

    private BigDecimal longitude;

    @Column(nullable = false)
    private String timezone;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EventStatus status;

    @Column(name = "cover_url")
    private String coverUrl;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Version
    @Column(nullable = false)
    private int version;

    protected Event() {
    }

    private Event(UUID id, UUID organizerId, short categoryId, String title, String description,
                  String venueName, String address, Double latitude, Double longitude,
                  String timezone, Instant startsAt, Instant endsAt) {
        this.id = id;
        this.organizerId = organizerId;
        this.categoryId = categoryId;
        this.title = title;
        this.description = description;
        this.venueName = venueName;
        this.address = address;
        setGeo(latitude, longitude);
        this.timezone = timezone;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.status = EventStatus.DRAFT;
    }

    public static Event create(UUID organizerId, short categoryId, String title, String description,
                               String venueName, String address, Double latitude, Double longitude,
                               String timezone, Instant startsAt, Instant endsAt) {
        requireValidTitle(title);
        requireValidDates(startsAt, endsAt);
        return new Event(Uuids.v7(), organizerId, categoryId, title, description == null ? "" : description,
                venueName, address, latitude, longitude, timezone, startsAt, endsAt);
    }

    /** DRAFT → PUBLISHED. Requiere al menos una tarifa (la política se crea con el evento). */
    public void publish(boolean hasTariffs) {
        if (!status.canTransitionTo(EventStatus.PUBLISHED) || status != EventStatus.DRAFT) {
            throw new EventNotDraftException("Solo un evento en DRAFT puede publicarse (estado actual: " + status + ")");
        }
        if (!hasTariffs) {
            throw new EventNotPublishableException("El evento necesita al menos una tarifa para publicarse");
        }
        this.status = EventStatus.PUBLISHED;
    }

    /**
     * Aplica un PATCH parcial. Publicado solo admite campos seguros
     * (description, coverUrl, address, startsAt/endsAt — cambio de horario = reprogramación).
     *
     * @return true si hubo cambio de horario sobre un evento publicado (⇒ emitir EventRescheduled)
     */
    public boolean applyUpdate(EventUpdate update) {
        if (status != EventStatus.DRAFT && update.touchesUnsafeFieldsForPublished()) {
            throw new EventFieldNotEditableException();
        }
        update.title().ifPresent(t -> {
            requireValidTitle(t);
            this.title = t;
        });
        update.description().ifPresent(d -> this.description = d);
        update.categoryId().ifPresent(c -> this.categoryId = c);
        update.venueName().ifPresent(v -> this.venueName = v);
        if (update.addressPresent()) {
            this.address = update.address();
        }
        if (update.geoPresent()) {
            setGeo(update.latitude(), update.longitude());
        }
        update.timezone().ifPresent(tz -> this.timezone = tz);
        if (update.coverUrlPresent()) {
            this.coverUrl = update.coverUrl();
        }
        boolean scheduleChanged = false;
        if (update.touchesSchedule()) {
            Instant newStarts = update.startsAt().orElse(this.startsAt);
            Instant newEnds = update.endsAt().orElse(this.endsAt);
            requireValidDates(newStarts, newEnds);
            scheduleChanged = !newStarts.equals(this.startsAt) || !newEnds.equals(this.endsAt);
            this.startsAt = newStarts;
            this.endsAt = newEnds;
        }
        return scheduleChanged && status != EventStatus.DRAFT;
    }

    /** Soft delete — solo DRAFT (frozen: DELETE /organizer/events solo borradores). */
    public void softDelete(Instant now) {
        if (status != EventStatus.DRAFT) {
            throw new EventNotDraftException("Solo un evento en DRAFT puede eliminarse");
        }
        this.deletedAt = now;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public boolean isOwnedBy(UUID userId) {
        return organizerId.equals(userId);
    }

    private void setGeo(Double latitude, Double longitude) {
        if ((latitude == null) != (longitude == null)) {
            throw new IllegalArgumentException("latitude y longitude deben venir en pareja");
        }
        this.latitude = latitude == null ? null : BigDecimal.valueOf(latitude);
        this.longitude = longitude == null ? null : BigDecimal.valueOf(longitude);
    }

    private static void requireValidTitle(String title) {
        if (title == null || title.length() < 3 || title.length() > 200) {
            throw new IllegalArgumentException("title debe tener entre 3 y 200 caracteres");
        }
    }

    private static void requireValidDates(Instant startsAt, Instant endsAt) {
        if (startsAt == null || endsAt == null || !endsAt.isAfter(startsAt)) {
            throw new IllegalArgumentException("ends_at debe ser posterior a starts_at");
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrganizerId() {
        return organizerId;
    }

    public short getCategoryId() {
        return categoryId;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public String getVenueName() {
        return venueName;
    }

    public String getAddress() {
        return address;
    }

    public BigDecimal getLatitude() {
        return latitude;
    }

    public BigDecimal getLongitude() {
        return longitude;
    }

    public String getTimezone() {
        return timezone;
    }

    public Instant getStartsAt() {
        return startsAt;
    }

    public Instant getEndsAt() {
        return endsAt;
    }

    public EventStatus getStatus() {
        return status;
    }

    public String getCoverUrl() {
        return coverUrl;
    }

    public int getVersion() {
        return version;
    }
}
