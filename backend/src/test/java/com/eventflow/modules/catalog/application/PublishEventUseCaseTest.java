package com.eventflow.modules.catalog.application;

import com.eventflow.modules.catalog.domain.Event;
import com.eventflow.modules.catalog.domain.EventStatus;
import com.eventflow.modules.catalog.domain.event.EventPublished;
import com.eventflow.modules.catalog.domain.exception.EventNotFoundException;
import com.eventflow.modules.catalog.domain.exception.EventNotPublishableException;
import com.eventflow.modules.catalog.domain.port.CategoryRepository;
import com.eventflow.modules.catalog.domain.port.EventRepository;
import com.eventflow.modules.catalog.domain.port.TariffsReadPort;
import com.eventflow.shared.outbox.OutboxPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PublishEventUseCaseTest {

    @Mock EventRepository eventRepository;
    @Mock CategoryRepository categoryRepository;
    @Mock TariffsReadPort tariffs;
    @Mock OutboxPublisher outbox;

    private PublishEventUseCase useCase;
    private UUID organizerId;
    private Event event;

    @BeforeEach
    void setUp() {
        organizerId = UUID.randomUUID();
        event = Event.create(organizerId, (short) 1, "Evento Test", "d", "Venue", null, null, null,
                "America/Panama", Instant.parse("2027-01-01T20:00:00Z"), Instant.parse("2027-01-01T23:00:00Z"));
        CatalogValidations validations = new CatalogValidations(eventRepository, categoryRepository);
        useCase = new PublishEventUseCase(eventRepository, tariffs, validations,
                mock(EventDetailAssembler.class), outbox);
    }

    @Test
    void publishes_draft_with_tariffs_and_emits_outbox_event() {
        when(eventRepository.findById(event.getId())).thenReturn(Optional.of(event));
        when(eventRepository.save(event)).thenReturn(event);
        when(tariffs.eventHasTariffs(event.getId())).thenReturn(true);

        useCase.execute(organizerId, event.getId());

        assertThat(event.getStatus()).isEqualTo(EventStatus.PUBLISHED);
        verify(outbox).publish(eq("Event"), eq(event.getId()), eq(EventPublished.TYPE),
                eq(EventPublished.VERSION), eq(organizerId), anyMap());
    }

    @Test
    void rejects_publish_without_tariffs() {
        when(eventRepository.findById(event.getId())).thenReturn(Optional.of(event));
        when(tariffs.eventHasTariffs(event.getId())).thenReturn(false);

        assertThatThrownBy(() -> useCase.execute(organizerId, event.getId()))
                .isInstanceOf(EventNotPublishableException.class);
        verify(outbox, never()).publish(any(), any(), any(), anyInt(), any(), anyMap());
    }

    @Test
    void foreign_event_is_404_anti_enumeration() {
        when(eventRepository.findById(event.getId())).thenReturn(Optional.of(event));

        assertThatThrownBy(() -> useCase.execute(UUID.randomUUID(), event.getId()))
                .isInstanceOf(EventNotFoundException.class);
    }
}
