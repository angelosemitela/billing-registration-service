package com.aalvarenga.billing.exception;

import org.springframework.http.HttpStatus;

/**
 * Exceção lançada sempre que uma regra de negócio do enunciado é violada
 * (campo obrigatório ausente, valor fora do domínio, cálculo que não bate,
 * registro duplicado, referência inexistente, etc).
 *
 * <p>Todo lugar do código que precisa "recusar" uma requisição lança esta
 * exceção com o {@link HttpStatus} mais adequado da família 4XX (ver
 * javadoc de cada fábrica estática abaixo) e uma mensagem em INGLÊS (o
 * enunciado exige que o campo "reason" da resposta seja sempre em inglês).
 * A exceção é capturada centralizadamente em {@link GlobalExceptionHandler},
 * que monta o JSON de erro padronizado - assim nenhuma camada de serviço
 * precisa se preocupar em montar a resposta HTTP, apenas em "reclamar" do
 * problema encontrado.
 */
public class BusinessException extends RuntimeException {

    private final HttpStatus status;

    public BusinessException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }

    /** 400 - campo obrigatório ausente, formato inválido, valor fora do enum/domínio. */
    public static BusinessException badRequest(String message) {
        return new BusinessException(HttpStatus.BAD_REQUEST, message);
    }

    /** 404 - uma referência informada (ex: account.id) não existe na base. */
    public static BusinessException notFound(String message) {
        return new BusinessException(HttpStatus.NOT_FOUND, message);
    }

    /** 409 - conflito com um registro já existente (protocolo, externalId, transactionId duplicados). */
    public static BusinessException conflict(String message) {
        return new BusinessException(HttpStatus.CONFLICT, message);
    }

    /** 412 - uma pré-condição de cálculo não foi atendida (somatórios, fórmulas de valores). */
    public static BusinessException preconditionFailed(String message) {
        return new BusinessException(HttpStatus.PRECONDITION_FAILED, message);
    }
}
