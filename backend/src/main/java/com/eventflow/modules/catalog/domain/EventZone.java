package com.eventflow.modules.catalog.domain;

import com.eventflow.shared.domain.Uuids;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

/** Zona física del evento (capacidad propia). Las tarifas pueden referenciarla. */
@Entity
@Table(name = "event_zones", schema = "catalog")
public class EventZone {

    @Id
    private UUID id;

    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private int capacity;

    protected EventZone() {
    }

    private EventZone(UUID id, UUID eventId, String name, int capacity) {
        this.id = id;
        this.eventId = eventId;
        this.name = name;
        this.capacity = capacity;
    }

    public static EventZone create(UUID eventId, String name, int capacity) {
        if (name == null || name.isBlank() || name.length() > 80) {
            throw new IllegalArgumentException("name es obligatorio (máx 80)");
        }
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity debe ser ≥ 1");
        }
        return new EventZone(Uuids.v7(), eventId, name, capacity);
    }

    public UUID getId() {
        return id;
    }

    public UUID getEventId() {
        return eventId;
    }

    public String getName() {
        return name;
    }

    public int getCapacity() {
        return capacity;
    }
}
