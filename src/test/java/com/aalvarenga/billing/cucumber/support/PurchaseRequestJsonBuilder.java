package com.aalvarenga.billing.cucumber.support;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Monta e "mutila" (de propósito, para os cenários de erro) o corpo JSON da
 * requisição de compra usado pelos testes funcionais/E2E.
 *
 * <p>Reaproveita a fixture {@code src/test/resources/cucumber/compra-base.json}
 * - uma requisição 100% válida, com o mínimo de campos necessário para
 * passar em TODAS as regras de {@code PurchaseValidationService} - como
 * ponto de partida. A partir dela:
 * <ul>
 *   <li>{@link #buildBaseRequestJson()} substitui os placeholders
 *       ({@code __PROTOCOL__} etc.) por valores únicos (ver
 *       {@link UniqueTestData}), para o cenário de ciclo completo (feliz);</li>
 *   <li>{@link #applyFieldOverride(String, String, String)} pega esse JSON
 *       já válido e altera UM campo específico para um valor inválido/ausente,
 *       para os cenários de erro (ver {@code erros-de-validacao.feature}) -
 *       cada regra de negócio vira uma LINHA de tabela (Examples), em vez de
 *       um método Java novo por regra. É o que torna essa suíte fácil de
 *       ESCALAR: adicionar cobertura para uma regra nova de
 *       {@code PurchaseValidationService} é, na maioria dos casos, uma linha
 *       a mais na tabela do Gherkin, não uma classe/método Java novo.</li>
 * </ul>
 *
 * <h2>Sintaxe de caminho suportada</h2>
 * <p>Deliberadamente LIMITADA (mesma filosofia de "ratchet" já usada no
 * projeto para o piso de cobertura do JaCoCo/limiar do SpotBugs - começar
 * simples, ampliar só quando um cenário de verdade precisar): cobre
 * {@code "nomeDoCampo"} (nível raiz) e {@code "arrayField[indice].campo"}
 * (um nível de lista + um campo folha) - o suficiente para expressar TODAS
 * as regras de validação hoje existentes (que estão sempre em
 * {@code account[0].*}, {@code product[0].*}, {@code payment[0].*} ou
 * {@code billing[0].*}). Não é um motor de JSONPath genérico.
 */
@Component
@RequiredArgsConstructor
public class PurchaseRequestJsonBuilder {

    private static final String FIXTURE_RESOURCE = "/cucumber/compra-base.json";

    /** Casa "campo" OU "campo[indice]" - um único segmento de caminho. */
    private static final Pattern PATH_SEGMENT = Pattern.compile("^(\\w+)(\\[(\\d+)])?$");

    /** Ver javadoc de {@link #applyFieldOverride(String, String, String)} para o significado de cada token. */
    private static final String TOKEN_NULO = "(nulo)";
    private static final String TOKEN_AUSENTE = "(ausente)";
    private static final String TOKEN_VAZIO = "(vazio)";

    // Mesmo bean de Jackson 3 (JsonMapper) usado no restante da aplicação
    // (ver PurchaseService/GlobalExceptionHandler) - reaproveitar o bean já
    // configurado pelo Spring, em vez de instanciar um JsonMapper próprio
    // aqui, evita qualquer divergência de configuração (ex: módulos de data
    // registrados) entre o que a aplicação usa em produção e o que os testes
    // usam para montar/alterar payloads.
    private final JsonMapper objectMapper;

    /**
     * Gera um corpo de requisição válido, pronto para uso, com protocolo e
     * externalId ÚNICOS (ver {@link UniqueTestData} para o porquê).
     */
    public GeneratedPurchaseRequest buildBaseRequestJson() {
        String protocol = UniqueTestData.uniqueValue("PROTO");
        String externalId = UniqueTestData.uniqueValue("EXT");
        String tokenId = UniqueTestData.uniqueValue("TOKEN");
        String transactionId = UniqueTestData.uniqueValue("TXN");

        String json = readTemplateResource()
                .replace("__TRANSACTION_DT__", UniqueTestData.nowAsEpochMillis())
                .replace("__PROTOCOL__", protocol)
                .replace("__EXTERNAL_ID__", externalId)
                .replace("__TOKEN_ID__", tokenId)
                .replace("__TRANSACTION_ID__", transactionId);

        return new GeneratedPurchaseRequest(json, protocol, externalId);
    }

    /**
     * Devolve um NOVO JSON (o original não é alterado) com o campo indicado
     * por {@code path} sobrescrito por {@code rawValue}.
     *
     * <p>Três valores de {@code rawValue} são tratados como TOKENS especiais
     * em vez de texto literal (representam o que uma tabela do Gherkin, em
     * texto puro, não consegue expressar diretamente):
     * <ul>
     *   <li>{@code "(nulo)"} - define o campo como {@code null} JSON (o
     *       equivalente a "campo presente, mas vazio de valor");</li>
     *   <li>{@code "(ausente)"} - REMOVE o campo do JSON (o equivalente a
     *       "o campo nem foi enviado" - cobre validações como
     *       {@code account.isAuthorizedFallback is required});</li>
     *   <li>{@code "(vazio)"} - define como string vazia {@code ""} (cobre
     *       validações de "não pode ser blank").</li>
     * </ul>
     * Qualquer outro valor é interpretado automaticamente como booleano,
     * número (inteiro ou decimal) ou texto, pela própria forma da string -
     * ex: {@code "true"} vira {@code true} JSON, {@code "-10"} vira um
     * número, {@code "VISA"} continua string.
     */
    public String applyFieldOverride(String json, String path, String rawValue) {
        JsonNode root = objectMapper.readTree(json);
        String[] segments = path.split("\\.");

        JsonNode current = root;
        ObjectNode targetObject = null;
        String targetField = null;

        for (int i = 0; i < segments.length; i++) {
            Matcher matcher = PATH_SEGMENT.matcher(segments[i]);
            if (!matcher.matches()) {
                throw new IllegalArgumentException(
                        "Segmento de caminho não suportado: \"" + segments[i] + "\" (caminho completo: \"" + path + "\")");
            }
            String fieldName = matcher.group(1);
            String indexGroup = matcher.group(3);
            boolean isLastSegment = i == segments.length - 1;

            if (isLastSegment && indexGroup == null) {
                targetObject = (ObjectNode) current;
                targetField = fieldName;
                break;
            }

            JsonNode fieldNode = current.get(fieldName);
            current = indexGroup != null ? fieldNode.get(Integer.parseInt(indexGroup)) : fieldNode;
        }

        applyValue(targetObject, targetField, rawValue);
        return objectMapper.writeValueAsString(root);
    }

    private void applyValue(ObjectNode target, String fieldName, String rawValue) {
        if (TOKEN_NULO.equals(rawValue)) {
            target.putNull(fieldName);
        } else if (TOKEN_AUSENTE.equals(rawValue)) {
            target.remove(fieldName);
        } else if (TOKEN_VAZIO.equals(rawValue)) {
            target.put(fieldName, "");
        } else if ("true".equals(rawValue) || "false".equals(rawValue)) {
            target.put(fieldName, Boolean.parseBoolean(rawValue));
        } else if (rawValue.matches("-?\\d+")) {
            target.put(fieldName, Long.parseLong(rawValue));
        } else if (rawValue.matches("-?\\d+\\.\\d+")) {
            target.put(fieldName, new BigDecimal(rawValue));
        } else {
            target.put(fieldName, rawValue);
        }
    }

    private String readTemplateResource() {
        try (InputStream inputStream = getClass().getResourceAsStream(FIXTURE_RESOURCE)) {
            if (inputStream == null) {
                throw new IllegalStateException("Fixture não encontrada no classpath: " + FIXTURE_RESOURCE);
            }
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao ler a fixture de requisição base (" + FIXTURE_RESOURCE + ")", e);
        }
    }
}
