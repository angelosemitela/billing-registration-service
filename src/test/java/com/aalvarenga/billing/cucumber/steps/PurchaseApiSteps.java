package com.aalvarenga.billing.cucumber.steps;

import com.aalvarenga.billing.cucumber.support.GeneratedPurchaseRequest;
import com.aalvarenga.billing.cucumber.support.PurchaseRequestJsonBuilder;
import com.aalvarenga.billing.entity.AccountEntity;
import com.aalvarenga.billing.entity.ProductEntity;
import com.aalvarenga.billing.repository.AccountRepository;
import com.aalvarenga.billing.repository.BillRepository;
import com.aalvarenga.billing.repository.ProductRepository;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.pt.Dado;
import io.cucumber.java.pt.Então;
import io.cucumber.java.pt.Quando;
import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;
import lombok.RequiredArgsConstructor;
import org.assertj.core.api.SoftAssertions;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Step definitions COMPARTILHADAS pelas duas *.feature desta suíte
 * ({@code registro-e-consulta-de-compra.feature} e
 * {@code erros-de-validacao.feature}) - o Cucumber casa cada linha Gherkin
 * com um método aqui pelo TEXTO da anotação, não pelo arquivo .feature de
 * origem, então não há problema (e sim vantagem: menos duplicação) em uma
 * classe só cobrir os passos de ambas.
 *
 * <p>Sem {@code @Component}/{@code @Service}: o {@code cucumber-spring}
 * descobre esta classe pelo pacote configurado em {@code RunCucumberIT}
 * ({@code GLUE_PROPERTY_NAME}) e a registra como um bean Spring "por conta
 * própria" (com um escopo especial, uma instância NOVA por cenário) - é
 * assim que {@code @RequiredArgsConstructor} (injeção via construtor)
 * funciona aqui exatamente como em qualquer {@code @Service} do projeto.
 *
 * <p>Os campos de instância abaixo (nenhum {@code static}) são o "mundo" do
 * cenário: como esta classe é recriada a cada cenário (ver acima), eles
 * nunca vazam estado de um cenário para o outro - só o BANCO DE DADOS é
 * compartilhado entre cenários (ver {@code SpringIntegrationConfig}), e é
 * exatamente por isso que {@link PurchaseRequestJsonBuilder} sempre gera
 * identificadores únicos.
 */
@RequiredArgsConstructor
public class PurchaseApiSteps {

    private final PurchaseRequestJsonBuilder requestBuilder;
    private final AccountRepository accountRepository;
    private final ProductRepository productRepository;
    private final BillRepository billRepository;

    private GeneratedPurchaseRequest generatedRequest;
    private String requestJson;
    private Response lastResponse;
    // Guardado à parte de "lastResponse" (que o passo de consulta SOBRESCREVE
    // logo em seguida) - o cenário de correspondência campo a campo (ver
    // osCamposAbaixoDevemCorresponder) precisa comparar TRÊS fontes ao mesmo
    // tempo: o JSON enviado (requestJson), a resposta da criação (este campo)
    // e a resposta da consulta (lastResponse, no momento em que aquele passo roda).
    private Response purchaseResponse;
    private AccountEntity persistedAccount;
    private List<ProductEntity> persistedProducts;
    private int persistedBillCount;

    // ------------------------------------------------------------------
    // Preparação da requisição (compartilhado pelas duas features)
    // ------------------------------------------------------------------

    @Dado("uma requisição de compra válida com protocolo e externalId únicos")
    public void umaRequisicaoDeCompraValidaComProtocoloEExternalIdUnicos() {
        generatedRequest = requestBuilder.buildBaseRequestJson();
        requestJson = generatedRequest.json();
    }

    /**
     * Único ponto de "mutação" usado pelos cenários de erro (ver
     * {@code erros-de-validacao.feature}) - transforma a requisição, até
     * aqui 100% válida, invalidando UM campo específico. Ver javadoc de
     * {@link PurchaseRequestJsonBuilder#applyFieldOverride(String, String, String)}
     * para a sintaxe de caminho e os tokens especiais ({@code (nulo)},
     * {@code (ausente)}, {@code (vazio)}) aceitos na coluna "valor" da
     * tabela de exemplos.
     */
    @Dado("o campo {string} da requisição é definido como {string}")
    public void oCampoDaRequisicaoEDefinidoComo(String campo, String valor) {
        requestJson = requestBuilder.applyFieldOverride(requestJson, campo, valor);
    }

    // ------------------------------------------------------------------
    // Chamadas HTTP
    //
    // Nota (18/09/2026): a barra "/" tem significado ESPECIAL em Cucumber
    // Expressions - fora de chaves ({string}/{int}...), ela marca uma
    // ALTERNATIVA ("gato/cachorro" casa "gato" OU "cachorro"), igual "|" em
    // regex. Um caminho de URL como "/api/v1/purchases" tem barras que não
    // são alternativas nenhuma - por isso cada uma precisa ser ESCAPADA com
    // "\/" (que em uma string Java literal se escreve "\\/") para o Cucumber
    // tratá-la como texto comum. Sem isso, o erro só aparece em tempo de
    // EXECUÇÃO (não de compilação, já que é só uma String para o javac) -
    // e como o Cucumber monta as expressões de TODOS os step definitions de
    // uma vez só antes do primeiro cenário, um único erro desses derruba a
    // suíte inteira com "step undefined" em toda e qualquer linha Gherkin,
    // mesmo em passos sem nenhuma relação com o problema - foi exatamente
    // o sintoma visto no primeiro `mvn verify` real. Alternativa que
    // evitaria essa pegadinha por completo: usar uma expressão regex pura
    // (com barra invertida dupla e `^...$`) em vez de Cucumber Expression
    // nesses dois métodos - mais verboso, mas sem caracteres "mágicos".
    // ------------------------------------------------------------------

    @Quando("a compra é registrada via POST \\/api\\/v1\\/purchases")
    public void aCompraERegistradaViaPost() {
        lastResponse = given()
                .contentType(ContentType.JSON)
                .body(requestJson)
                .when()
                .post("/api/v1/purchases");
        purchaseResponse = lastResponse;
    }

    @Quando("a conta é consultada via POST \\/api\\/v1\\/purchases\\/query pelo externalId do cenário")
    public void aContaEConsultadaViaPostQueryPeloExternalIdDoCenario() {
        String queryBody = "{\"externalId\": \"" + generatedRequest.externalId() + "\"}";
        lastResponse = given()
                .contentType(ContentType.JSON)
                .body(queryBody)
                .when()
                .post("/api/v1/purchases/query");
    }

    /**
     * Segunda forma de consultar (além da que já existe acima, pelo
     * {@code externalId}): usa o {@code productId} DEVOLVIDO pela criação da
     * compra ({@code product[0].id} de {@link #purchaseResponse}) como
     * entrada da consulta - exatamente o encadeamento pedido pelo usuário
     * ("o productId gerado na primeira parte será a entrada do productId do
     * endpoint purchases/query"). Os 3 flags de retorno e o
     * {@code maxBillReturn} vêm fixos porque o cenário que usa este passo
     * quer o payload de consulta INTEIRO de volta, para validar campo a
     * campo (ver {@link #osCamposAbaixoDevemCorresponder(DataTable)}).
     */
    @Quando("o produto gerado é consultado via POST \\/api\\/v1\\/purchases\\/query com todos os retornos habilitados")
    public void oProdutoGeradoEConsultadoViaPostQueryComTodosOsRetornosHabilitados() {
        String productId = purchaseResponse.jsonPath().getString("product[0].id");
        String queryBody = """
                {
                  "productId": "%s",
                  "returnProductData": true,
                  "returnPaymentData": true,
                  "returnBillData": true,
                  "maxBillReturn": 0
                }
                """.formatted(productId);
        lastResponse = given()
                .contentType(ContentType.JSON)
                .body(queryBody)
                .when()
                .post("/api/v1/purchases/query");
    }

    // ------------------------------------------------------------------
    // Assertions sobre a resposta HTTP (reaproveitadas pelas duas chamadas
    // acima, e pelos cenários de erro - PurchaseResponse e QueryResponse
    // compartilham os campos "result"/"code"/"reason", ver DTOs)
    // ------------------------------------------------------------------

    @Então("a resposta deve ter status HTTP {int} e result {string}")
    public void aRespostaDeveTerStatusHttpEResult(int statusEsperado, String resultEsperado) {
        assertThat(lastResponse.statusCode()).isEqualTo(statusEsperado);
        assertThat(lastResponse.jsonPath().getString("result")).isEqualTo(resultEsperado);
    }

    @Então("a razão da resposta deve conter {string}")
    public void aRazaoDaRespostaDeveConter(String trechoEsperado) {
        assertThat(lastResponse.jsonPath().getString("reason")).contains(trechoEsperado);
    }

    // ------------------------------------------------------------------
    // Assertions diretas no banco (passo 2 do ciclo pedido: "validação dos
    // dados persistidos em base") - via os MESMOS repositórios Spring Data
    // que a aplicação usa, injetados aqui como em qualquer @Service.
    // ------------------------------------------------------------------

    @Então("a conta deve existir na base de dados com o externalId gerado para o cenário")
    public void aContaDeveExistirNaBaseDeDadosComOExternalIdGeradoParaOCenario() {
        persistedAccount = accountRepository.findByExternalId(generatedRequest.externalId())
                .orElseThrow(() -> new AssertionError(
                        "Nenhuma conta encontrada na base com externalId=" + generatedRequest.externalId()));
    }

    @Então("os produtos da compra devem existir na base de dados vinculados a essa conta")
    public void osProdutosDaCompraDevemExistirNaBaseDeDadosVinculadosAEssaConta() {
        persistedProducts = productRepository.findByAccountId(persistedAccount.getId());
        assertThat(persistedProducts).isNotEmpty();
    }

    @Então("as faturas da compra devem existir na base de dados vinculadas a esses produtos")
    public void asFaturasDaCompraDevemExistirNaBaseDeDadosVinculadasAEssesProdutos() {
        List<Long> productIds = persistedProducts.stream().map(ProductEntity::getId).toList();
        persistedBillCount = billRepository.findByProductIdInOrderByDueDtDesc(productIds).size();
        assertThat(persistedBillCount).isPositive();
    }

    // ------------------------------------------------------------------
    // Assertions sobre a resposta da consulta (passo 3 do ciclo pedido:
    // "validação da consulta pelo purchase/query da massa criada")
    // ------------------------------------------------------------------

    @Então("a consulta deve devolver a mesma quantidade de produtos que foram persistidos")
    public void aConsultaDeveDevolverAMesmaQuantidadeDeProdutosQueForamPersistidos() {
        List<Object> products = lastResponse.jsonPath().getList("products");
        assertThat(products).hasSameSizeAs(persistedProducts);
    }

    @Então("a consulta deve devolver a mesma quantidade de faturas que foram persistidas")
    public void aConsultaDeveDevolverAMesmaQuantidadeDeFaturasQueForamPersistidas() {
        List<Object> bill = lastResponse.jsonPath().getList("bill");
        assertThat(bill).hasSize(persistedBillCount);
    }

    // ------------------------------------------------------------------
    // Correspondência campo a campo entre requisição/respostas (pedido do
    // usuário em 21/09/2026 - matriz de rastreabilidade originalmente
    // recebida como planilha; ver decisoes.md para o relato completo,
    // incluindo as poucas correções de nome de campo que a planilha
    // precisou - ex: a consulta devolve "accountId"/"paymentId"/"billId",
    // não "id" como a criação da compra, e a lista de produtos da consulta
    // se chama "products" no plural, não "product").
    //
    // Cada linha da tabela do Gherkin tem 3 colunas de "onde buscar o
    // valor" (entrada.purchases / saída.purchases / saída.purchases/query -
    // "null" nelas significa "esta fonte não se aplica a este campo", igual
    // já usado à mão na planilha original) e uma coluna "valor" com dois
    // significados possíveis:
    //   - "Igual": todos os valores das colunas aplicáveis daquela linha
    //     precisam bater entre si;
    //   - qualquer outra coisa é um valor FIXO esperado (sintaxe: "texto"
    //     entre aspas, (null)/(false)/(true), ou um número puro) - usado
    //     pelas linhas que já nascem calculadas/fixas na consulta (status,
    //     saldo/estorno zerados etc.), sem nenhum "espelho" na compra.
    // ------------------------------------------------------------------

    /** Entidades cujo valor só pode ser comparado pelos dígitos finais, porque a consulta devolve o valor MASCARADO. */
    private static final Set<String> ENTIDADES_MASCARADAS_2_DIGITOS = Set.of("ACCOUNT.DOCUMENT.VALUE");
    private static final Set<String> ENTIDADES_MASCARADAS_4_DIGITOS = Set.of("PAYMENT.CARDNUMBER");

    private static final String COLUNA_NAO_SE_APLICA = "null";

    /** Um valor resolvido de uma linha da tabela, já com a origem para aparecer numa mensagem de falha legível. */
    private record CampoValor(String origem, String valor) {
    }

    @Então("os campos abaixo devem corresponder entre a requisição de compra, a resposta da compra e a resposta da consulta:")
    public void osCamposAbaixoDevemCorresponder(DataTable dataTable) {
        JsonPath requestPath = new JsonPath(requestJson);
        JsonPath purchaseResponsePath = purchaseResponse.jsonPath();
        JsonPath queryResponsePath = lastResponse.jsonPath();

        SoftAssertions softly = new SoftAssertions();
        for (Map<String, String> row : dataTable.asMaps()) {
            String entidade = row.get("entidade");
            List<CampoValor> valores = new ArrayList<>();
            adicionaSeAplicavel(valores, "entrada.purchases", row.get("entrada.purchases"), requestPath);
            adicionaSeAplicavel(valores, "saída.purchases", row.get("saída.purchases"), purchaseResponsePath);
            adicionaSeAplicavel(valores, "saída.purchases/query", row.get("saída.purchases/query"), queryResponsePath);

            String valorEsperado = row.get("valor");
            if ("Igual".equalsIgnoreCase(valorEsperado)) {
                verificaTodosIguaisEntreSi(softly, entidade, valores);
            } else {
                verificaTodosContraValorFixo(softly, entidade, valorEsperado, valores);
            }
        }
        softly.assertAll();
    }

    private void adicionaSeAplicavel(List<CampoValor> valores, String origem, String caminho, JsonPath fonte) {
        if (caminho == null || COLUNA_NAO_SE_APLICA.equalsIgnoreCase(caminho.trim())) {
            return; // esta coluna não tem correspondente para este campo (ver cabeçalho da seção acima)
        }
        valores.add(new CampoValor(origem, fonte.getString(caminho)));
    }

    private void verificaTodosIguaisEntreSi(SoftAssertions softly, String entidade, List<CampoValor> valores) {
        softly.assertThat(valores.size())
                .as("Campo %s: a linha da tabela precisa ter pelo menos 2 colunas aplicáveis para comparar (\"Igual\")", entidade)
                .isGreaterThanOrEqualTo(2);
        CampoValor referencia = valores.getFirst();
        for (int i = 1; i < valores.size(); i++) {
            CampoValor outro = valores.get(i);
            softly.assertThat(valoresBatem(entidade, referencia.valor(), outro.valor()))
                    .as("Campo %s: %s=\"%s\" deveria ser igual a %s=\"%s\"",
                            entidade, referencia.origem(), referencia.valor(), outro.origem(), outro.valor())
                    .isTrue();
        }
    }

    private void verificaTodosContraValorFixo(SoftAssertions softly, String entidade, String valorEsperadoBruto, List<CampoValor> valores) {
        for (CampoValor valor : valores) {
            softly.assertThat(bateComValorFixo(valorEsperadoBruto, valor.valor()))
                    .as("Campo %s (%s): esperado %s, veio \"%s\"", entidade, valor.origem(), valorEsperadoBruto, valor.valor())
                    .isTrue();
        }
    }

    /**
     * Compara dois valores já resolvidos de uma mesma linha - igualdade
     * direta na maioria dos casos, com 2 exceções: (a) campos numéricos
     * comparados por VALOR (via {@link BigDecimal#compareTo}, não por texto -
     * "110" e "110.00" são o mesmo valor, só formatado diferente pela
     * camada de persistência) e (b) as 2 entidades mascaradas pela consulta
     * (ver {@link #ENTIDADES_MASCARADAS_2_DIGITOS}/{@link #ENTIDADES_MASCARADAS_4_DIGITOS}),
     * onde só os últimos dígitos (os únicos que sobrevivem à máscara)
     * precisam bater.
     */
    private boolean valoresBatem(String entidade, String a, String b) {
        if (a == null || b == null) {
            return a == null && b == null;
        }
        if (ENTIDADES_MASCARADAS_2_DIGITOS.contains(entidade)) {
            return sufixo(a, 2).equals(sufixo(b, 2));
        }
        if (ENTIDADES_MASCARADAS_4_DIGITOS.contains(entidade)) {
            return sufixo(a, 4).equals(sufixo(b, 4));
        }
        if (ehNumerico(a) && ehNumerico(b)) {
            return new BigDecimal(a).compareTo(new BigDecimal(b)) == 0;
        }
        return a.equals(b);
    }

    /** Mesma sintaxe de valor fixo já documentada no cabeçalho de campos com "Valor" solto na planilha original. */
    private boolean bateComValorFixo(String especificacao, String valorReal) {
        String spec = especificacao.trim();
        if ("(null)".equals(spec)) {
            return valorReal == null;
        }
        if ("(false)".equals(spec) || "(true)".equals(spec)) {
            return spec.equals("(" + valorReal + ")");
        }
        if (spec.length() >= 2 && spec.startsWith("\"") && spec.endsWith("\"")) {
            return spec.substring(1, spec.length() - 1).equals(valorReal);
        }
        if (ehNumerico(spec) && valorReal != null && ehNumerico(valorReal)) {
            return new BigDecimal(spec).compareTo(new BigDecimal(valorReal)) == 0;
        }
        return spec.equals(valorReal);
    }

    private String sufixo(String valor, int tamanho) {
        return valor.length() <= tamanho ? valor : valor.substring(valor.length() - tamanho);
    }

    private boolean ehNumerico(String valor) {
        return valor.matches("-?\\d+(\\.\\d+)?");
    }
}
