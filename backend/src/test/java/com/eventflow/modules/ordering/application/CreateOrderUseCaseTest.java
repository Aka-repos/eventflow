package com.eventflow.modules.ordering.application;

import com.eventflow.modules.catalog.application.CatalogFacade;
import com.eventflow.modules.ordering.application.command.CreateOrderCommand;
import com.eventflow.modules.ordering.application.result.OrderResult;
import com.eventflow.modules.ordering.domain.OrderStatus;
import com.eventflow.modules.ordering.domain.event.OrderEvents;
import com.eventflow.modules.ordering.domain.exception.EventSoldOutException;
import com.eventflow.modules.ordering.domain.port.OrderRepository;
import com.eventflow.modules.ticketing.application.TicketingFacade;
import com.eventflow.shared.config.PlatformConfig;
import com.eventflow.shared.domain.Money;
import com.eventflow.shared.error.SemanticValidationException;
import com.eventflow.shared.outbox.OutboxPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreateOrderUseCaseTest {

    private static final Instant NOW = Instant.parse("2027-01-01T12:00:00Z");

    @Mock OrderRepository orderRepository;
    @Mock TicketingFacade ticketing;
    @Mock CatalogFacade catalog;
    @Mock OutboxPublisher outbox;
    @Mock PlatformConfig config;

    private CreateOrderUseCase useCase;
    private final UUID buyerId = UUID.randomUUID();
    private final UUID tariffId = UUID.randomUUID();
    private final UUID eventId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        useCase = new CreateOrderUseCase(orderRepository, ticketing, catalog, outbox, config,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void creates_pending_order_with_ttl_and_emits_order_created() {
        when(config.intValue("defaults.order_expiration_minutes", "minutes", 15)).thenReturn(15);
        when(ticketing.reserve(tariffId, 2, NOW)).thenReturn(
                new TicketingFacade.TariffSnapshot(tariffId, eventId, "VIP", Money.of("80.00", "USD")));
        when(catalog.purchaseSnapshot(eventId)).thenReturn(new CatalogFacade.EventPurchaseSnapshot(
                eventId, "Concierto X", UUID.randomUUID(), "PUBLISHED", NOW.plusSeconds(86400), Map.of()));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        OrderResult result = useCase.execute(new CreateOrderCommand(buyerId, UUID.randomUUID(),
                List.of(new CreateOrderCommand.Item("TICKET", tariffId, 2))));

        assertThat(result.order().getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(result.order().getTotal()).isEqualTo(Money.of("160.00", "USD"));
        assertThat(result.order().getExpiresAt()).isEqualTo(NOW.plusSeconds(15 * 60));
        assertThat(result.descriptionsByItem().values()).containsExactly("VIP — Concierto X");
        verify(outbox).publish(eq("Order"), any(), eq(OrderEvents.ORDER_CREATED), eq(1), eq(buyerId), anyMap());
    }

    @Test
    void sold_out_tariff_propagates_conflict_and_saves_nothing() {
        when(ticketing.reserve(tariffId, 1, NOW)).thenThrow(new SoldOutStub());

        assertThatThrownBy(() -> useCase.execute(new CreateOrderCommand(buyerId, UUID.randomUUID(),
                List.of(new CreateOrderCommand.Item("TICKET", tariffId, 1)))))
                .hasMessageContaining("VIP");
        verify(orderRepository, never()).save(any());
    }

    @Test
    void event_not_on_sale_is_rejected() {
        when(ticketing.reserve(tariffId, 1, NOW)).thenReturn(
                new TicketingFacade.TariffSnapshot(tariffId, eventId, "VIP", Money.of("80.00", "USD")));
        when(catalog.purchaseSnapshot(eventId)).thenReturn(new CatalogFacade.EventPurchaseSnapshot(
                eventId, "Concierto X", UUID.randomUUID(), "CANCELLED", NOW, Map.of()));

        assertThatThrownBy(() -> useCase.execute(new CreateOrderCommand(buyerId, UUID.randomUUID(),
                List.of(new CreateOrderCommand.Item("TICKET", tariffId, 1)))))
                .isInstanceOf(EventSoldOutException.class);
    }

    /** Doble de la excepción de ticketing (mismo ErrorCode) — el test no importa dominio ajeno (doc 10). */
    private static class SoldOutStub extends com.eventflow.shared.error.DomainException {
        SoldOutStub() {
            super(com.eventflow.shared.error.ErrorCode.EVENT_SOLD_OUT, "Sin cupo disponible para la tarifa VIP");
        }
    }

    @Test
    void parking_and_exchange_items_are_rejected_until_their_module() {
        assertThatThrownBy(() -> useCase.execute(new CreateOrderCommand(buyerId, UUID.randomUUID(),
                List.of(new CreateOrderCommand.Item("PARKING", UUID.randomUUID(), 1)))))
                .isInstanceOf(SemanticValidationException.class);
        verify(ticketing, never()).reserve(any(), anyInt(), any());
    }
}
