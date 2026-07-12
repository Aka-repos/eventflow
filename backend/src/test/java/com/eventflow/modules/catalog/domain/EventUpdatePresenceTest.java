package com.eventflow.modules.catalog.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** El PATCH distingue presencia: address/coverUrl/geo se aplican solo si vienen en el request. */
class EventUpdatePresenceTest {

    private Event event() {
        return Event.create(UUID.randomUUID(), (short) 1, "Evento Presencia", "d", "Venue",
                "Dirección original", 8.5, -80.0, "America/Panama",
                Instant.parse("2027-05-01T20:00:00Z"), Instant.parse("2027-05-01T22:00:00Z"));
    }

    @Test
    void absent_fields_do_not_touch_state() {
        Event e = event();
        e.applyUpdate(EventUpdate.builder().description("solo descripción").build());
        assertThat(e.getAddress()).isEqualTo("Dirección original");
        assertThat(e.getLatitude()).isNotNull();
        assertThat(e.getCoverUrl()).isNull();
    }

    @Test
    void present_nullable_fields_are_applied() {
        Event e = event();
        e.applyUpdate(EventUpdate.builder().address("Nueva dirección").coverUrl("https://img/1.jpg")
                .geo(9.0, -79.0).build());
        assertThat(e.getAddress()).isEqualTo("Nueva dirección");
        assertThat(e.getCoverUrl()).isEqualTo("https://img/1.jpg");
        assertThat(e.getLatitude().doubleValue()).isEqualTo(9.0);
    }

    @Test
    void unsafe_detection_covers_each_field() {
        assertThat(EventUpdate.builder().title("T").build().touchesUnsafeFieldsForPublished()).isTrue();
        assertThat(EventUpdate.builder().categoryId((short) 2).build().touchesUnsafeFieldsForPublished()).isTrue();
        assertThat(EventUpdate.builder().venueName("V").build().touchesUnsafeFieldsForPublished()).isTrue();
        assertThat(EventUpdate.builder().timezone("UTC").build().touchesUnsafeFieldsForPublished()).isTrue();
        assertThat(EventUpdate.builder().geo(1.0, 1.0).build().touchesUnsafeFieldsForPublished()).isTrue();
        assertThat(EventUpdate.builder().description("d").address("a").coverUrl("c")
                .startsAt(Instant.EPOCH).build().touchesUnsafeFieldsForPublished()).isFalse();
    }
}
