package com.aalvarenga.billing.entity.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Converte um {@code boolean} Java para o formato de armazenamento pedido no
 * enunciado (instrução 2): coluna {@code CHAR(1)} onde {@code "1"} = true e
 * {@code "0"} = false.
 *
 * <p>Usar um {@link AttributeConverter} do JPA é a forma "boa prática" de
 * resolver esse tipo de mapeamento: a entidade Java continua trabalhando com
 * {@code Boolean} normalmente (legível, seguro, com autocomplete da IDE) e
 * é o Hibernate quem aplica a conversão de/para {@code CHAR(1)}
 * automaticamente, de forma centralizada e reaproveitável em todas as
 * colunas "*_B" do projeto (basta anotar o campo com
 * {@code @Convert(converter = BooleanCharConverter.class)}).
 */
@Converter
public class BooleanCharConverter implements AttributeConverter<Boolean, String> {

    @Override
    public String convertToDatabaseColumn(Boolean attribute) {
        if (attribute == null) {
            return null;
        }
        return attribute ? "1" : "0";
    }

    @Override
    public Boolean convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }
        return "1".equals(dbData);
    }
}
