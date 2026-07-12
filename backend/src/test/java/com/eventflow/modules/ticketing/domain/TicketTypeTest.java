package com.eventflow.modules.ticketing.domain;

import com.eventflow.modules.ticketing.domain.exception.TicketTypeHasSalesException;
import com.eventflow.shared.domain.Money;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Reglas de la tarifa: sin ventas se edita libre; con vendidos solo cambios seguros. */
class TicketTypeTest {

    private TicketType tariff() {
        return TicketType.create(UUID.randomUUID(), "General", "Acceso general",
                Money.of("25.00", "USD"), null, 100, null, null);
    }

    @Test
    void create_validates_quantity_and_price() {
        assertThat(tariff().getSoldQuantity()).isZero();
        assertThatThrownBy(() -> TicketType.create(UUID.randomUUID(), "X", null,
                Money.of("10.00", "USD"), null, 0, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void update_without_sales_allows_everything() {
        TicketType t = tariff();
        t.update("VIP", "desc", Money.of("90.00", "USD"), UUID.randomUUID(), 50, null, null);
        assertThat(t.getName()).isEqualTo("VIP");
        assertThat(t.getPrice()).isEqualTo(Money.of("90.00", "USD"));
        assertThat(t.getTotalQuantity()).isEqualTo(50);
    }

    @Test
    void update_with_sales_rejects_price_zone_name_changes() {
        TicketType t = tariff();
        t.reserve(1, java.time.Instant.now());

        assertThatThrownBy(() -> t.update("Otro nombre", null, Money.of("25.00", "USD"), null, 100, null, null))
                .isInstanceOf(TicketTypeHasSalesException.class);
        assertThatThrownBy(() -> t.update("General", null, Money.of("30.00", "USD"), null, 100, null, null))
                .isInstanceOf(TicketTypeHasSalesException.class);
        assertThatThrownBy(() -> t.update("General", null, Money.of("25.00", "USD"), UUID.randomUUID(), 100, null, null))
                .isInstanceOf(TicketTypeHasSalesException.class);
    }

    @Test
    void update_with_sales_allows_description_window_and_quantity_increase() {
        TicketType t = tariff();
        t.reserve(1, java.time.Instant.now());
        t.update("General", "Nueva desc", Money.of("25.00", "USD"), null, 150, null, null);
        assertThat(t.getTotalQuantity()).isEqualTo(150);
        assertThat(t.getDescription()).isEqualTo("Nueva desc");
    }

    @Test
    void update_with_sales_rejects_quantity_below_sold() {
        TicketType t = tariff();
        t.reserve(2, java.time.Instant.now());
        assertThatThrownBy(() -> t.update("General", null, Money.of("25.00", "USD"), null, 1, null, null))
                .isInstanceOf(TicketTypeHasSalesException.class);
    }

    @Test
    void delete_guard_rejects_when_sold() {
        TicketType t = tariff();
        t.reserve(1, java.time.Instant.now());
        assertThatThrownBy(t::ensureDeletable).isInstanceOf(TicketTypeHasSalesException.class);
    }
}
