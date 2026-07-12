package com.eventflow.modules.catalog.domain;

import com.eventflow.modules.catalog.domain.exception.EventFieldNotEditableException;
import com.eventflow.modules.catalog.domain.exception.EventNotDraftException;
import com.eventflow.modules.catalog.domain.exception.EventNotPublishableException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Reglas del agregado Event: máquina de estados (design/04 §3) y edición segura tras publicar. */
class EventTest {

    private static final Instant STARTS = Instant.parse("2027-01-10T20:00:00Z");
    private static final Instant ENDS = Instant.parse("2027-01-10T23:00:00Z");

    private Event draftEvent() {
        return Event.create(UUID.randomUUID(), (short) 1, "Concierto de Prueba", "Descripción",
                "Estadio Nacional", "Calle 50", null, null, "America/Panama", STARTS, ENDS);
    }

    @Test
    void create_starts_in_draft_with_uuid_v7() {
        Event event = draftEvent();
        assertThat(event.getStatus()).isEqualTo(EventStatus.DRAFT);
        assertThat(event.getId()).isNotNull();
        assertThat(event.getId().version()).isEqualTo(7);
    }

    @Test
    void create_rejects_ends_before_starts() {
        assertThatThrownBy(() -> Event.create(UUID.randomUUID(), (short) 1, "Titulo valido", "d",
                "Venue", null, null, null, "America/Panama", ENDS, STARTS))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void create_rejects_partial_geo_pair() {
        assertThatThrownBy(() -> Event.create(UUID.randomUUID(), (short) 1, "Titulo valido", "d",
                "Venue", null, 8.98, null, "America/Panama", STARTS, ENDS))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void publish_requires_draft_and_tariffs() {
        Event event = draftEvent();
        event.publish(true);
        assertThat(event.getStatus()).isEqualTo(EventStatus.PUBLISHED);

        assertThatThrownBy(() -> event.publish(true))
                .isInstanceOf(EventNotDraftException.class);
    }

    @Test
    void publish_without_tariffs_is_rejected() {
        assertThatThrownBy(() -> draftEvent().publish(false))
                .isInstanceOf(EventNotPublishableException.class);
    }

    @Test
    void update_in_draft_allows_all_fields() {
        Event event = draftEvent();
        event.applyUpdate(EventUpdate.builder()
                .title("Nuevo Título").categoryId((short) 2).venueName("Otro Venue")
                .timezone("America/Bogota").build());
        assertThat(event.getTitle()).isEqualTo("Nuevo Título");
        assertThat(event.getCategoryId()).isEqualTo((short) 2);
        assertThat(event.getVenueName()).isEqualTo("Otro Venue");
    }

    @Test
    void update_published_allows_only_safe_fields() {
        Event event = draftEvent();
        event.publish(true);

        event.applyUpdate(EventUpdate.builder().description("Actualizada").coverUrl("https://img").build());
        assertThat(event.getDescription()).isEqualTo("Actualizada");

        assertThatThrownBy(() -> event.applyUpdate(EventUpdate.builder().title("Otro").build()))
                .isInstanceOf(EventFieldNotEditableException.class);
        assertThatThrownBy(() -> event.applyUpdate(EventUpdate.builder().categoryId((short) 3).build()))
                .isInstanceOf(EventFieldNotEditableException.class);
        assertThatThrownBy(() -> event.applyUpdate(EventUpdate.builder().venueName("V2").build()))
                .isInstanceOf(EventFieldNotEditableException.class);
    }

    @Test
    void update_published_dates_is_reschedule() {
        Event event = draftEvent();
        event.publish(true);
        boolean rescheduled = event.applyUpdate(EventUpdate.builder()
                .startsAt(STARTS.plusSeconds(3600)).endsAt(ENDS.plusSeconds(3600)).build());
        assertThat(rescheduled).isTrue();
        assertThat(event.getStartsAt()).isEqualTo(STARTS.plusSeconds(3600));
    }

    @Test
    void update_draft_dates_is_not_reschedule() {
        boolean rescheduled = draftEvent().applyUpdate(EventUpdate.builder()
                .startsAt(STARTS.plusSeconds(60)).build());
        assertThat(rescheduled).isFalse();
    }

    @Test
    void update_rejects_inverted_dates_combining_current_state() {
        Event event = draftEvent();
        assertThatThrownBy(() -> event.applyUpdate(EventUpdate.builder().startsAt(ENDS.plusSeconds(1)).build()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void soft_delete_only_in_draft() {
        Event event = draftEvent();
        event.softDelete(Instant.now());
        assertThat(event.isDeleted()).isTrue();

        Event published = draftEvent();
        published.publish(true);
        assertThatThrownBy(() -> published.softDelete(Instant.now()))
                .isInstanceOf(EventNotDraftException.class);
    }

    @Test
    void status_machine_rejects_invalid_transitions() {
        assertThat(EventStatus.DRAFT.canTransitionTo(EventStatus.PUBLISHED)).isTrue();
        assertThat(EventStatus.PUBLISHED.canTransitionTo(EventStatus.SOLD_OUT)).isTrue();
        assertThat(EventStatus.SOLD_OUT.canTransitionTo(EventStatus.PUBLISHED)).isTrue();
        assertThat(EventStatus.PUBLISHED.canTransitionTo(EventStatus.IN_PROGRESS)).isTrue();
        assertThat(EventStatus.IN_PROGRESS.canTransitionTo(EventStatus.FINISHED)).isTrue();
        assertThat(EventStatus.SUSPENDED.canTransitionTo(EventStatus.PUBLISHED)).isTrue();

        assertThat(EventStatus.DRAFT.canTransitionTo(EventStatus.SOLD_OUT)).isFalse();
        assertThat(EventStatus.FINISHED.canTransitionTo(EventStatus.PUBLISHED)).isFalse();
        assertThat(EventStatus.CANCELLED.canTransitionTo(EventStatus.PUBLISHED)).isFalse();
        assertThat(EventStatus.PUBLISHED.canTransitionTo(EventStatus.DRAFT)).isFalse();
    }
}
