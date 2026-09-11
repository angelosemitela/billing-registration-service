package com.aalvarenga.billing.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InstallmentSplitServiceTest {

    private final InstallmentSplitService service = new InstallmentSplitService();

    @Test
    void singleInstallment_returnsTheWholeValue() {
        List<BigDecimal> result = service.split(new BigDecimal("20.00"), 1);

        assertThat(result).containsExactly(new BigDecimal("20.00"));
    }

    @Test
    void evenSplit_distributesEquallyAcrossInstallments() {
        List<BigDecimal> result = service.split(new BigDecimal("30.00"), 3);

        assertThat(result).containsExactly(
                new BigDecimal("10.00"), new BigDecimal("10.00"), new BigDecimal("10.00"));
    }

    @Test
    void unevenSplit_putsTheRemainderOnTheFirstInstallment() {
        // 20.00 / 3 = 6.66 com resto de 0.02 -> primeira parcela absorve o resto
        List<BigDecimal> result = service.split(new BigDecimal("20.00"), 3);

        assertThat(result).containsExactly(
                new BigDecimal("6.68"), new BigDecimal("6.66"), new BigDecimal("6.66"));

        BigDecimal sum = result.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(sum).isEqualByComparingTo("20.00");
    }
}
