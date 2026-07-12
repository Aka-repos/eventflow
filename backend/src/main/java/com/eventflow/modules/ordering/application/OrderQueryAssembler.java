package com.eventflow.modules.ordering.application;

import com.eventflow.modules.catalog.application.CatalogFacade;
import com.eventflow.modules.ordering.domain.Order;
import com.eventflow.modules.ordering.domain.OrderItem;
import com.eventflow.modules.ticketing.application.TicketingFacade;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Datos calculados de OrderResponse. Composición en BATCH (H3): para una página de órdenes se
 * hacen 3 consultas (tarifas, títulos, ticketIds) en lugar de N por ítem.
 */
@Component
public class OrderQueryAssembler {

    private final TicketingFacade ticketing;
    private final CatalogFacade catalog;

    public OrderQueryAssembler(TicketingFacade ticketing, CatalogFacade catalog) {
        this.ticketing = ticketing;
        this.catalog = catalog;
    }

    public Map<UUID, String> descriptions(Order order) {
        return descriptionsFor(List.of(order));
    }

    public Map<UUID, List<UUID>> ticketIds(Order order) {
        return ticketIdsFor(List.of(order));
    }

    /** "Tarifa — Evento" para todos los ítems de la página, con 2 consultas. */
    public Map<UUID, String> descriptionsFor(Collection<Order> orders) {
        List<UUID> tariffIds = orders.stream().flatMap(o -> o.getItems().stream())
                .filter(i -> "TICKET".equals(i.getItemType()))
                .map(OrderItem::getTicketTypeId)
                .distinct()
                .toList();
        Map<UUID, TicketingFacade.TariffSnapshot> tariffs = ticketing.tariffSnapshots(tariffIds);
        Map<UUID, String> titles = catalog.eventTitles(
                tariffs.values().stream().map(TicketingFacade.TariffSnapshot::eventId).distinct().toList());
        Map<UUID, String> descriptions = new HashMap<>();
        for (Order order : orders) {
            for (OrderItem item : order.getItems()) {
                TicketingFacade.TariffSnapshot tariff = tariffs.get(item.getTicketTypeId());
                if (tariff != null) {
                    descriptions.put(item.getId(),
                            tariff.name() + " — " + titles.getOrDefault(tariff.eventId(), ""));
                }
            }
        }
        return descriptions;
    }

    /** ticketIds de todos los ítems de la página en una consulta. */
    public Map<UUID, List<UUID>> ticketIdsFor(Collection<Order> orders) {
        List<UUID> itemIds = orders.stream().flatMap(o -> o.getItems().stream())
                .map(OrderItem::getId)
                .toList();
        return ticketing.ticketIdsBySourceOrderItem(itemIds);
    }
}
