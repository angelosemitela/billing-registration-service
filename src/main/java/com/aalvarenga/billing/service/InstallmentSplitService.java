package com.aalvarenga.billing.service;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Responsável por ratear um valor monetário entre N parcelas, seguindo a
 * regra do enunciado: "sempre considerar a divisão inteira para todas as
 * parcelas, colocando o valor remanescente na primeira parcela. Somatório de
 * todos os registros deve bater com o campo original".
 *
 * <p>Trabalhamos em CENTAVOS (valores inteiros - {@code long}) em vez de
 * {@link BigDecimal} fracionário durante a divisão, porque divisão inteira de
 * centavos é exata e livre de qualquer surpresa de arredondamento; só no
 * final convertemos cada parcela de volta para {@link BigDecimal} com 2 casas
 * decimais. Esse é o motivo de existir uma classe própria para isso, IN
 * VEZ de espalhar essa conta em cada lugar que precisa parcelar um valor
 * (produto, desconto, taxa) - reaproveitamento de código (instrução 3).
 */
@Service
public class InstallmentSplitService {

    /**
     * Divide {@code totalValue} em {@code installmentCount} parcelas.
     *
     * @param totalValue       valor total a ser rateado
     * @param installmentCount número de parcelas (deve ser >= 1)
     * @return lista com {@code installmentCount} valores, na ordem das
     * parcelas (1ª parcela absorve o resto da divisão inteira em centavos)
     */
    public List<BigDecimal> split(BigDecimal totalValue, int installmentCount) {
        if (installmentCount < 1) {
            throw new IllegalArgumentException("installmentCount must be >= 1");
        }
        if (installmentCount == 1) {
            return List.of(totalValue.setScale(2, RoundingMode.HALF_UP));
        }

        long totalCents = totalValue.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact();
        long baseCents = totalCents / installmentCount;
        long remainderCents = totalCents % installmentCount;

        List<BigDecimal> installments = new ArrayList<>(installmentCount);
        for (int i = 0; i < installmentCount; i++) {
            long cents = baseCents + (i == 0 ? remainderCents : 0);
            installments.add(BigDecimal.valueOf(cents, 2));
        }
        return installments;
    }
}
