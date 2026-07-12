package com.eventflow.modules.catalog.application;

import com.eventflow.modules.catalog.application.command.UpdateEventCommand;
import com.eventflow.modules.catalog.domain.Event;
import com.eventflow.modules.catalog.domain.EventUpdate;
import com.eventflow.modules.catalog.domain.event.EventRescheduled;
import com.eventflow.modules.catalog.domain.port.CategoryRepository;
import com.eventflow.modules.catalog.domain.port.EventRepository;
import com.eventflow.shared.error.VersionConflictException;
import com.eventflow.shared.outbox.OutboxPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UpdateEventUseCaseTest {

    private static final Instant STARTS = Instant.parse("2027-03-01T20:00:00Z");
    private static final Instant ENDS = Instant.parse("2027-03-01T23:00:00Z");

    @Mock EventRepository eventRepository;
    @Mock CategoryRepository categoryRepository;
    @Mock OutboxPublisher outbox;

    private UpdateEventUseCase useCase;
    private UUID organizerId;
    private Event event;

    @BeforeEach
    void setUp() {
        organizerId = UUID.randomUUID();
        event = Event.create(organizerId, (short) 1, "Evento Test", "d", "Venue", null, null, null,
                "America/Panama", STARTS, ENDS);
        useCase = new UpdateEventUseCase(eventRepository,
                new CatalogValidations(eventRepository, categoryRepository),
                mock(EventDetailAssembler.class), outbox);
    }

    @Test
    void if_match_mismatch_is_version_conflict_with_current_version() {
        when(eventRepository.findById(event.getId())).thenReturn(Optional.of(event));

        UpdateEventCommand cmd = new UpdateEventCommand(organizerId, event.getId(), 7,
                EventUpdate.builder().description("x").build(), null, null);

        assertThatThrownBy(() -> useCase.execute(cmd))
                .isInstanceOf(VersionConflictException.class)
                .extracting(e -> ((VersionConflictException) e).currentVersion())
                .isEqualTo(0);
    }

    @Test
    void rescheduling_published_event_emits_event_rescheduled() {
        event.publish(true);
        when(eventRepository.findById(event.getId())).thenReturn(Optional.of(event));
        when(eventRepository.save(event)).thenReturn(event);

        useCase.execute(new UpdateEventCommand(organizerId, event.getId(), 0,
                EventUpdate.builder().startsAt(STARTS.plusSeconds(7200)).endsAt(ENDS.plusSeconds(7200)).build(),
                null, null));

        verify(outbox).publish(eq("Event"), eq(event.getId()), eq(EventRescheduled.TYPE),
                eq(EventRescheduled.VERSION), eq(organizerId), anyMap());
    }
}
