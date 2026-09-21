package com.aalvarenga.billing.util;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cobre {@link EpochDateUtil} - conversão central entre "epoch millis" (o
 * único formato de data/hora usado no banco, ver javadoc da classe) e
 * {@link ZonedDateTime}.
 *
 * <p>{@link EpochDateUtil#nowMillis()} é a única parte "impura" da classe
 * (depende do relógio do sistema) - por isso é verificada por uma janela de
 * tolerância (delta), nunca por igualdade exata, o que já é uma boa prática
 * para qualquer teste que dependa de {@code Instant.now()}. As demais
 * conversões são funções puras e determinísticas, testadas por "ida e
 * volta" (roundtrip) e por um valor fixo conhecido.
 */
class EpochDateUtilTest {

    @Test
    void nowMillis_returnsCurrentTimeWithinToleranceWindow() {
        long before = Instant.now().toEpochMilli();

        long result = EpochDateUtil.nowMillis();

        long after = Instant.now().toEpochMilli();
        assertThat(result).isBetween(before, after);
    }

    @Test
    void toBusinessDateTime_usesSaoPauloTimeZone() {
        // 01/01/2026 00:00:00 UTC-03:00 (America/Sao_Paulo, sem horário de
        // verão desde 2019) = 03:00:00 UTC no mesmo dia.
        ZonedDateTime expected = ZonedDateTime.of(2026, 1, 1, 0, 0, 0, 0, EpochDateUtil.BUSINESS_ZONE);
        long epochMillis = expected.toInstant().toEpochMilli();

        ZonedDateTime result = EpochDateUtil.toBusinessDateTime(epochMillis);

        assertThat(result.getZone()).isEqualTo(EpochDateUtil.BUSINESS_ZONE);
        assertThat(result).isEqualTo(expected);
    }

    @Test
    void toEpochMillis_convertsBackToTheSameInstant() {
        ZonedDateTime dateTime = ZonedDateTime.of(2026, 9, 21, 12, 30, 0, 0, ZoneOffset.UTC);

        long result = EpochDateUtil.toEpochMillis(dateTime);

        assertThat(result).isEqualTo(dateTime.toInstant().toEpochMilli());
    }

    @Test
    void roundtrip_toBusinessDateTimeThenToEpochMillis_returnsOriginalValue() {
        long originalEpochMillis = 1_800_000_000_000L;

        long result = EpochDateUtil.toEpochMillis(EpochDateUtil.toBusinessDateTime(originalEpochMillis));

        assertThat(result).isEqualTo(originalEpochMillis);
    }
}
