package com.aalvarenga.billing.util;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class CardExpirationUtilTest {

    @Test
    void parsesAValidExpiration() {
        Optional<YearMonth> result = CardExpirationUtil.parse("03/31");

        assertThat(result).contains(YearMonth.of(2031, 3));
    }

    @Test
    void rejectsAnInvalidMonth() {
        // Exemplo citado no enunciado: "13/29 (Não existe mês 13)"
        Optional<YearMonth> result = CardExpirationUtil.parse("13/29");

        assertThat(result).isEmpty();
    }

    @Test
    void detectsAnAlreadyExpiredCard() {
        // Exemplo citado no enunciado: "12/24 (Cartão já expirado)", considerando uma
        // data de referência posterior a dezembro/2024.
        YearMonth expiration = CardExpirationUtil.parse("12/24").orElseThrow();

        boolean expired = CardExpirationUtil.isExpired(expiration, LocalDate.of(2026, 9, 11));

        assertThat(expired).isTrue();
    }

    @Test
    void rejectsMalformedInput() {
        assertThat(CardExpirationUtil.parse("2029-03")).isEmpty();
        assertThat(CardExpirationUtil.parse(null)).isEmpty();
    }
}
