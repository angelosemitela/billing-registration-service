package com.aalvarenga.billing.cucumber;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;

import static io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.PLUGIN_PROPERTY_NAME;

/**
 * Ponto de entrada dos testes funcionais/E2E para o maven-failsafe-plugin
 * (ver pom.xml, seção {@code <build>}, comentário do
 * {@code maven-failsafe-plugin}).
 *
 * <p>Esta classe não contém nenhuma lógica própria - ela apenas configura,
 * via anotações da JUnit Platform Suite API, ONDE procurar os arquivos
 * {@code .feature} ({@link SelectClasspathResource}) e ONDE procurar as
 * classes de "step definitions" que implementam cada linha Gherkin
 * ({@link ConfigurationParameter} com {@code GLUE_PROPERTY_NAME}). O
 * {@link IncludeEngines} diz à JUnit Platform para usar o motor de execução
 * do Cucumber (trazido pela dependência {@code cucumber-junit-platform-engine},
 * ver pom.xml) em vez do motor padrão do Jupiter.
 *
 * <p>O sufixo {@code IT} (Integration Test) no nome NÃO é estético: é o que
 * faz o {@code maven-failsafe-plugin} descobrir esta classe (seu padrão
 * padrão de inclusão é {@code **&#47;*IT.java}) - o {@code maven-surefire-plugin}
 * (que roda os testes de unidade na fase {@code test}) usa um padrão
 * diferente ({@code **&#47;*Test.java}) e por isso NUNCA tenta rodar esta
 * classe, mesmo sem nenhuma exclusão explícita.
 */
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "com.aalvarenga.billing.cucumber")
// "pretty" (console) sempre existiu; "html"/"json" foram acrescentados em
// 21/09/2026 para dar ao Cucumber um RELATÓRIO PRÓPRIO em arquivo (ver
// README, seção "Evoluções pedidas") - antes disso, o único jeito de
// confirmar que os cenários rodaram era abrir o log bruto (texto solto) do
// step "mvn verify" no CI, já que não existia nenhum arquivo gerado para
// publicar como artefato (diferente do JaCoCo/SpotBugs, que sempre tiveram
// seu próprio relatório em arquivo). Ver ci.yml para o step que publica
// "target/cucumber-reports/" como artefato baixável do GitHub Actions.
@ConfigurationParameter(key = PLUGIN_PROPERTY_NAME,
        value = "pretty, html:target/cucumber-reports/cucumber-report.html, json:target/cucumber-reports/cucumber-report.json")
public class RunCucumberIT {
}
