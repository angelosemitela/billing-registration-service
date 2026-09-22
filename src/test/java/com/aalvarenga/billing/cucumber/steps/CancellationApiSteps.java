package com.aalvarenga.billing.cucumber.steps;

import com.aalvarenga.billing.cucumber.support.CancellationRequestJsonBuilder;
import com.aalvarenga.billing.cucumber.support.GeneratedCancellationRequest;
import com.aalvarenga.billing.cucumber.support.PurchaseRequestJsonBuilder;
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

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Step definitions da feature de CANCELAMENTO ({@code POST /api/v1/purchases/cancel} -
 * pedido do usuário em 22/09/2026, ver {@code cancelamento.feature} e a
 * seção correspondente de {@code decisoes.md}/addendum do projeto).
 *
 * <p>Toda a preparação de "massa" (a compra que antecede o cancelamento) usa
 * os passos JÁ EXISTENTES de {@link PurchaseApiSteps}
 * ({@code "uma requisição de compra válida..."}, {@code "o campo ... da
 * requisição é definido como ..."}, {@code "a compra é registrada via
 * POST..."}, {@code "a resposta deve ter status HTTP..."}) - reaproveitados
 * tal qual nas *.feature, sem nenhuma duplicação de passo aqui. Esta classe
 * só injeta {@link PurchaseApiSteps} como dependência (mesma instância
 * "por cenário" do cucumber-spring) para poder recuperar a resposta daquela
 * compra através de {@link PurchaseApiSteps#getPurchaseResponse()} assim que
 * o cenário precisa montar o payload de cancelamento.
 *
 * <p>Reaproveita, também, {@link PurchaseRequestJsonBuilder#applyFieldOverride}
 * (já existente, agnóstico ao schema) para "mutilar" o JSON de cancelamento
 * nos cenários de erro - exatamente a mesma mecânica de
 * {@code erros-de-validacao.feature}, só que operando sobre o JSON de
 * cancelamento em vez do de compra.
 */
@RequiredArgsConstructor
public class CancellationApiSteps {

    private final PurchaseApiSteps purchaseApiSteps;
    private final CancellationRequestJsonBuilder cancellationRequestJsonBuilder;
    private final PurchaseRequestJsonBuilder purchaseRequestJsonBuilder;

    private GeneratedCancellationRequest generatedCancellationRequest;
    private String cancellationRequestJson;
    private Response lastCancellationResponse;

    // ------------------------------------------------------------------
    // Preparação da requisição de cancelamento
    // ------------------------------------------------------------------

    /**
     * Recupera {@code product[0].id}/{@code billing[0].id}/
     * {@code billing[0].chargedValue} da resposta da compra que acabou de
     * ser registrada (passo anterior, de {@link PurchaseApiSteps}) e monta,
     * a partir deles, o payload de cancelamento de exemplo fornecido pelo
     * usuário (ver {@code cancelamento-base.json}).
     */
    @Dado("a massa de cancelamento é preparada a partir da compra recém-registrada")
    public void aMassaDeCancelamentoEPreparadaAPartirDaCompraRecemRegistrada() {
        JsonPath purchaseResponse = purchaseApiSteps.getPurchaseResponse().jsonPath();
        generatedCancellationRequest = cancellationRequestJsonBuilder.buildBaseRequestJson(purchaseResponse);
        cancellationRequestJson = generatedCancellationRequest.json();
    }

    /**
     * Único ponto de "mutação" usado pelos cenários de erro de cancelamento -
     * mesma sintaxe/tokens de {@link PurchaseRequestJsonBuilder#applyFieldOverride(String, String, String)}
     * ({@code (nulo)}/{@code (ausente)}/{@code (vazio)}), agora aplicada ao
     * JSON de cancelamento em vez do de compra.
     */
    @Dado("o campo {string} da requisição de cancelamento é definido como {string}")
    public void oCampoDaRequisicaoDeCancelamentoEDefinidoComo(String campo, String valor) {
        cancellationRequestJson = purchaseRequestJsonBuilder.applyFieldOverride(cancellationRequestJson, campo, valor);
    }

    // ------------------------------------------------------------------
    // Chamada HTTP (barra escapada - ver nota equivalente em PurchaseApiSteps)
    // ------------------------------------------------------------------

    @Quando("o cancelamento é solicitado via POST \\/api\\/v1\\/purchases\\/cancel")
    public void oCancelamentoESolicitadoViaPost() {
        lastCancellationResponse = given()
                .contentType(ContentType.JSON)
                .body(cancellationRequestJson)
                .when()
                .post("/api/v1/purchases/cancel");
    }

    // ------------------------------------------------------------------
    // Assertions sobre a resposta de cancelamento
    //
    // Nomes de passo DIFERENTES dos equivalentes em PurchaseApiSteps
    // ("a resposta deve ter status HTTP..."/"a razão da resposta deve
    // conter...") de propósito: o Cucumber exige que o TEXTO de cada passo
    // seja único em toda a suíte (senão falha com "duplicate step
    // definitions" já na inicialização, antes de qualquer cenário rodar) -
    // como esta classe e PurchaseApiSteps são instâncias SEPARADAS (cada uma
    // com seu próprio campo de "última resposta"), reaproveitar o mesmo
    // texto aqui não funcionaria mesmo se a assinatura do método fosse
    // idêntica.
    // ------------------------------------------------------------------

    @Então("a resposta de cancelamento deve ter status HTTP {int} e result {string}")
    public void aRespostaDeCancelamentoDeveTerStatusHttpEResult(int statusEsperado, String resultEsperado) {
        assertThat(lastCancellationResponse.statusCode()).isEqualTo(statusEsperado);
        assertThat(lastCancellationResponse.jsonPath().getString("result")).isEqualTo(resultEsperado);
    }

    @Então("a razão da resposta de cancelamento deve conter {string}")
    public void aRazaoDaRespostaDeCancelamentoDeveConter(String trechoEsperado) {
        assertThat(lastCancellationResponse.jsonPath().getString("reason")).contains(trechoEsperado);
    }

    // ------------------------------------------------------------------
    // Correspondência campo a campo entre requisição e resposta de
    // cancelamento (cenários de SUCESSO - pedido do usuário em 22/09/2026,
    // baseado na planilha "Teste cancelamento").
    //
    // Mesma mecânica (e mesmo formato de tabela) já usada por
    // PurchaseApiSteps.osCamposAbaixoDevemCorresponder, com a diferença de
    // que o cancelamento só tem 2 "fontes" (requisição/resposta), não 3
    // (requisição/resposta da compra/resposta da consulta) - por isso um
    // método PRÓPRIO em vez de reaproveitar aquele diretamente. Ver o
    // javadoc de CancellationRequestJsonBuilder para o critério de quando
    // vale a pena extrair essa mecânica para uma classe utilitária
    // compartilhada (quando um terceiro consumidor aparecer).
    // ------------------------------------------------------------------

    private static final String COLUNA_NAO_SE_APLICA = "null";

    private record CampoValor(String origem, String valor) {
    }

    @Então("os campos abaixo devem corresponder entre a requisição de cancelamento e a resposta de cancelamento:")
    public void osCamposDeCancelamentoAbaixoDevemCorresponder(DataTable dataTable) {
        JsonPath requestPath = new JsonPath(cancellationRequestJson);
        JsonPath responsePath = lastCancellationResponse.jsonPath();

        SoftAssertions softly = new SoftAssertions();
        for (Map<String, String> row : dataTable.asMaps()) {
            String entidade = row.get("entidade");
            List<CampoValor> valores = new ArrayList<>();
            adicionaSeAplicavel(valores, "entrada.purchases/cancel", row.get("entrada.purchases/cancel"), requestPath);
            adicionaSeAplicavel(valores, "saída.purchases/cancel", row.get("saída.purchases/cancel"), responsePath);

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
            return;
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
            softly.assertThat(valoresBatem(referencia.valor(), outro.valor()))
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

    private boolean valoresBatem(String a, String b) {
        if (a == null || b == null) {
            return a == null && b == null;
        }
        if (ehNumerico(a) && ehNumerico(b)) {
            return new BigDecimal(a).compareTo(new BigDecimal(b)) == 0;
        }
        return a.equals(b);
    }

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

    private boolean ehNumerico(String valor) {
        return valor.matches("-?\\d+(\\.\\d+)?");
    }
}
