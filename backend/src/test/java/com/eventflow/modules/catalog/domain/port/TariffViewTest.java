package com.eventflow.modules.catalog.domain.port;

import com.eventflow.modules.catalog.domain.port.TariffsReadPort.TariffView;
import com.eventflow.shared.domain.Money;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** available = cupo restante Y ventana de venta abierta (O10: el cupo exacto no se expone). */
class TariffViewTest {

    private static final Instant NOW = Instant.parse("2027-01-15T12:00:00Z");

    private TariffView view(int total, int sold, Instant startsAt, Instant endsAt) {
        return new TariffView(UUID.randomUUID(), "General", null, Money.of("10.00", "USD"),
                null, total, sold, startsAt, endsAt);
    }

    @Test
    void available_with_stock_and_no_window() {
        assertThat(view(10, 9, null, null).isAvailable(NOW)).isTrue();
    }

    @Test
    void sold_out_is_unavailable() {
        assertThat(view(10, 10, null, null).isAvailable(NOW)).isFalse();
    }

    @Test
    void before_sales_start_is_unavailable() {
        assertThat(view(10, 0, NOW.plusSeconds(60), null).isAvailable(NOW)).isFalse();
        assertThat(view(10, 0, NOW, null).isAvailable(NOW)).isTrue();
    }

    @Test
    void after_sales_end_is_unavailable() {
        assertThat(view(10, 0, null, NOW).isAvailable(NOW)).isFalse();
        assertThat(view(10, 0, null, NOW.plusSeconds(1)).isAvailable(NOW)).isTrue();
    }
}
