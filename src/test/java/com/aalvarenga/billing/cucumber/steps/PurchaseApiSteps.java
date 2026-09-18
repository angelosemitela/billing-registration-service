package com.aalvarenga.billing.cucumber.steps;

import com.aalvarenga.billing.cucumber.support.GeneratedPurchaseRequest;
import com.aalvarenga.billing.cucumber.support.PurchaseRequestJsonBuilder;
import com.aalvarenga.billing.entity.AccountEntity;
import com.aalvarenga.billing.entity.ProductEntity;
import com.aalvarenga.billing.repository.AccountRepository;
import com.aalvarenga.billing.repository.BillRepository;
import com.aalvarenga.billing.repository.ProductRepository;
import io.cucumber.java.pt.Dado;
import io.cucumber.java.pt.Então;
import io.cucumber.java.pt.Quando;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import lombok.RequiredArgsConstructor;

import java.util.List;

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
}
