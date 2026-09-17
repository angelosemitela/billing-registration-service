package com.aalvarenga.billing.entity.converter;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cobre {@link BooleanCharConverter}: o {@link jakarta.persistence.AttributeConverter}
 * usado em todas as colunas {@code *_B} do projeto (ver javadoc da classe)
 * para mapear {@code Boolean} <-> {@code CHAR(1)} ({@code "1"}/{@code "0"}).
 * Outro utilitário puro e barato de testar - sem dependências, sem estado.
 */
class BooleanCharConverterTest {

    private final BooleanCharConverter converter = new BooleanCharConverter();

    @Test
    void convertsTrueToDatabaseColumn() {
        assertThat(converter.convertToDatabaseColumn(true)).isEqualTo("1");
    }

    @Test
    void convertsFalseToDatabaseColumn() {
        assertThat(converter.convertToDatabaseColumn(false)).isEqualTo("0");
    }

    @Test
    void convertsNullAttributeToNullColumn() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
    }

    @Test
    void convertsDatabaseOneToTrue() {
        assertThat(converter.convertToEntityAttribute("1")).isTrue();
    }

    @Test
    void convertsDatabaseZeroToFalse() {
        assertThat(converter.convertToEntityAttribute("0")).isFalse();
    }

    @Test
    void convertsNullColumnToNullAttribute() {
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }

    @Test
    void treatsAnyNonOneValueAsFalse_defensiveBehavior() {
        // O conversor não valida o conteúdo da coluna - qualquer coisa
        // diferente de "1" vira false (comportamento "fail-safe" implícito
        // em "1".equals(dbData), não um valor especial tratado à parte).
        assertThat(converter.convertToEntityAttribute("X")).isFalse();
    }
}
