package com.eventflow.modules.catalog.domain;

import java.time.Instant;
import java.util.Optional;

/**
 * Cambio parcial sobre el agregado Event (PATCH). Cada campo distingue "ausente" de "presente";
 * los presentes se validan contra el estado del evento (publicado ⇒ solo campos seguros).
 */
public final class EventUpdate {

    private final String title;
    private final String description;
    private final Short categoryId;
    private final String venueName;
    private final boolean addressPresent;
    private final String address;
    private final boolean geoPresent;
    private final Double latitude;
    private final Double longitude;
    private final String timezone;
    private final Instant startsAt;
    private final Instant endsAt;
    private final boolean coverUrlPresent;
    private final String coverUrl;

    private EventUpdate(Builder b) {
        this.title = b.title;
        this.description = b.description;
        this.categoryId = b.categoryId;
        this.venueName = b.venueName;
        this.addressPresent = b.addressPresent;
        this.address = b.address;
        this.geoPresent = b.geoPresent;
        this.latitude = b.latitude;
        this.longitude = b.longitude;
        this.timezone = b.timezone;
        this.startsAt = b.startsAt;
        this.endsAt = b.endsAt;
        this.coverUrlPresent = b.coverUrlPresent;
        this.coverUrl = b.coverUrl;
    }

    public Optional<String> title() {
        return Optional.ofNullable(title);
    }

    public Optional<String> description() {
        return Optional.ofNullable(description);
    }

    public Optional<Short> categoryId() {
        return Optional.ofNullable(categoryId);
    }

    public Optional<String> venueName() {
        return Optional.ofNullable(venueName);
    }

    public boolean addressPresent() {
        return addressPresent;
    }

    public String address() {
        return address;
    }

    public boolean geoPresent() {
        return geoPresent;
    }

    public Double latitude() {
        return latitude;
    }

    public Double longitude() {
        return longitude;
    }

    public Optional<String> timezone() {
        return Optional.ofNullable(timezone);
    }

    public Optional<Instant> startsAt() {
        return Optional.ofNullable(startsAt);
    }

    public Optional<Instant> endsAt() {
        return Optional.ofNullable(endsAt);
    }

    public boolean coverUrlPresent() {
        return coverUrlPresent;
    }

    public String coverUrl() {
        return coverUrl;
    }

    /** Campos que un evento PUBLICADO no admite (frozen: "publicado solo campos seguros"). */
    public boolean touchesUnsafeFieldsForPublished() {
        return title != null || categoryId != null || venueName != null || timezone != null || geoPresent;
    }

    public boolean touchesSchedule() {
        return startsAt != null || endsAt != null;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String title;
        private String description;
        private Short categoryId;
        private String venueName;
        private boolean addressPresent;
        private String address;
        private boolean geoPresent;
        private Double latitude;
        private Double longitude;
        private String timezone;
        private Instant startsAt;
        private Instant endsAt;
        private boolean coverUrlPresent;
        private String coverUrl;

        public Builder title(String v) {
            this.title = v;
            return this;
        }

        public Builder description(String v) {
            this.description = v;
            return this;
        }

        public Builder categoryId(Short v) {
            this.categoryId = v;
            return this;
        }

        public Builder venueName(String v) {
            this.venueName = v;
            return this;
        }

        public Builder address(String v) {
            this.addressPresent = true;
            this.address = v;
            return this;
        }

        public Builder geo(Double lat, Double lng) {
            this.geoPresent = true;
            this.latitude = lat;
            this.longitude = lng;
            return this;
        }

        public Builder timezone(String v) {
            this.timezone = v;
            return this;
        }

        public Builder startsAt(Instant v) {
            this.startsAt = v;
            return this;
        }

        public Builder endsAt(Instant v) {
            this.endsAt = v;
            return this;
        }

        public Builder coverUrl(String v) {
            this.coverUrlPresent = true;
            this.coverUrl = v;
            return this;
        }

        public EventUpdate build() {
            return new EventUpdate(this);
        }
    }
}
