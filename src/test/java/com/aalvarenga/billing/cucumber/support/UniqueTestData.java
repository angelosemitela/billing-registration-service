package com.aalvarenga.billing.cucumber.support;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Gera identificadores ÚNICOS para cada cenário de teste (protocolo,
 * externalId, transactionId, token id).
 *
 * <p><b>Por que isso é necessário</b>: o container de banco usado pelos
 * testes funcionais/E2E é compartilhado por TODA a suíte (ver
 * {@code SpringIntegrationConfig}, padrão "Singleton Container") - os dados
 * de um cenário continuam no banco quando o próximo cenário roda. Várias
 * regras de negócio deste projeto travam em VALOR DUPLICADO
 * ({@code protocol} já processado com sucesso, {@code account.externalId}
 * já registrado, {@code billing.transactionId} já registrado,
 * {@code payment.token.id} já registrado - ver
 * {@code PurchaseValidationService}) - sem identificadores únicos por
 * cenário, o segundo cenário que rodasse reaproveitando os mesmos valores do
 * primeiro tomaria um {@code 409 Conflict} inesperado. Esta é exatamente a
 * mesma estratégia (gerar dados novos a cada execução, em vez de depender de
 * um banco "limpo") já usada na ferramenta em shell script que este projeto
 * substitui - ver doc de decisões do projeto.
 *
 * <p>Usamos {@link UUID#randomUUID()} (imprevisível, praticamente impossível
 * de colidir mesmo em execuções paralelas futuras) combinado com um contador
 * sequencial por JVM ({@link AtomicLong}) apenas para deixar os valores mais
 * legíveis em log/depuração do que um UUID puro - o contador sozinho não
 * seria suficiente porque reinicia a cada execução da suíte, e o container
 * de banco pode ser reaproveitado entre execuções locais (Testcontainers
 * também suporta reutilização de container via {@code .withReuse(true)} -
 * ver README, seção de evoluções futuras).
 */
public final class UniqueTestData {

    private static final AtomicLong SEQUENCE = new AtomicLong();

    private UniqueTestData() {
    }

    /** Gera um valor único com um prefixo legível (ex: {@code "PROTO-3-a1b2c3d4"}). */
    public static String uniqueValue(String prefix) {
        long sequential = SEQUENCE.incrementAndGet();
        String shortRandom = UUID.randomUUID().toString().substring(0, 8);
        return prefix + "-" + sequential + "-" + shortRandom;
    }

    /** {@code transactionDt} precisa ser um epoch millis que nunca está no futuro (ver PurchaseValidationService). */
    public static String nowAsEpochMillis() {
        return String.valueOf(System.currentTimeMillis());
    }
}
