package com.eventflow.shared.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MoneyTest {

    @Test
    void should_normalize_scale_to_two_decimals_when_created() {
        // Given / When
        Money money = Money.of("72", "USD");
        // Then
        assertThat(money.amount()).isEqualByComparingTo(new BigDecimal("72.00"));
        assertThat(money.amount().scale()).isEqualTo(2);
        assertThat(money.currency()).isEqualTo("USD");
    }

    @Test
    void should_reject_negative_amounts() {
        assertThatThrownBy(() -> Money.of("-1.00", "USD"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_reject_invalid_currency() {
        assertThatThrownBy(() -> Money.of("1.00", "usd")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Money.of("1.00", null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_add_amounts_of_same_currency() {
        Money result = Money.of("80.00", "USD").add(Money.of("7.00", "USD"));
        assertThat(result).isEqualTo(Money.of("87.00", "USD"));
    }

    @Test
    void should_reject_operations_with_mixed_currencies() {
        assertThatThrownBy(() -> Money.of("1.00", "USD").add(Money.of("1.00", "EUR")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_subtract_and_never_go_negative() {
        Money result = Money.of("72.00", "USD").subtract(Money.of("3.60", "USD"));
        assertThat(result).isEqualTo(Money.of("68.40", "USD"));
        assertThatThrownBy(() -> Money.of("1.00", "USD").subtract(Money.of("2.00", "USD")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_apply_percentage_with_half_up_rounding() {
        // Regla oficial M1: fee = round_half_up(price × pct, 2)
        assertThat(Money.of("10.05", "USD").percentage(5)).isEqualTo(Money.of("0.50", "USD"));  // 0.5025 → 0.50
        assertThat(Money.of("10.15", "USD").percentage(5)).isEqualTo(Money.of("0.51", "USD"));  // 0.5075 → 0.51
    }

    @Test
    void should_guarantee_fee_plus_seller_equals_price() {
        // Invariante ck_transfers_money_squares: seller = price − fee (el residuo favorece al vendedor)
        Money price = Money.of("72.00", "USD");
        Money fee = price.percentage(5);
        Money seller = price.subtract(fee);
        assertThat(fee.add(seller)).isEqualTo(price);
    }
}
