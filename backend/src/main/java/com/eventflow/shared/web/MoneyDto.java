package com.eventflow.shared.web;

import com.eventflow.shared.domain.Money;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** Espejo del schema Money: amount como string decimal de 2 posiciones + ISO-4217. */
public record MoneyDto(
        @NotBlank @Pattern(regexp = "^\\d+\\.\\d{2}$", message = "amount debe ser un decimal con 2 posiciones") String amount,
        @NotBlank @Pattern(regexp = "^[A-Z]{3}$", message = "currency debe ser ISO-4217") String currency) {

    public static MoneyDto from(Money money) {
        return money == null ? null : new MoneyDto(money.amount().toPlainString(), money.currency());
    }

    public Money toMoney() {
        return Money.of(amount, currency);
    }
}
