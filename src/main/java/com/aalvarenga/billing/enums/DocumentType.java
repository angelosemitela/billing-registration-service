package com.aalvarenga.billing.enums;

/**
 * Tipos de documento de identificação aceitos para a estrutura
 * {@code account.document.type} da requisição de entrada.
 *
 * <p>Usar um enum (em vez de comparar Strings soltas em vários pontos do
 * código) é o que dá ao Jackson (a biblioteca de JSON usada pelo Spring) a
 * capacidade de REJEITAR automaticamente, já na desserialização do JSON de
 * entrada, qualquer valor que não esteja nesta lista - simplificando a
 * validação (ver com.aalvarenga.billing.exception.GlobalExceptionHandler,
 * que trata o erro de desserialização e devolve 4XX).
 */
public enum DocumentType {
    CPF,
    PASSPORT,
    UE,
    SSN,
    OTHER
}
