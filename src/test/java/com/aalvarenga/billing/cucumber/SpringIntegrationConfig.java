package com.aalvarenga.billing.cucumber;

import io.cucumber.spring.CucumberContextConfiguration;
import io.restassured.RestAssured;
import jakarta.annotation.PostConstruct;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

/**
 * Ponto de entrada da integração Cucumber &lt;-&gt; Spring Boot.
 *
 * <p>Esta classe não tem nenhum método {@code @Dado}/{@code @Quando}/
 * {@code @Então} - sua única função é a anotação {@link CucumberContextConfiguration}:
 * ela diz ao {@code cucumber-spring} "suba o contexto Spring configurado
 * abaixo, UMA VEZ, e reaproveite entre TODOS os cenários da suíte" -
 * exatamente o mesmo comportamento de cache de contexto que um
 * {@code @SpringBootTest} comum já tem entre classes de teste JUnit. Sem
 * isso, cada cenário Gherkin subiria (e derrubaria) a aplicação inteira e um
 * container Docker novo - inviável na prática.
 *
 * <p>Precisa haver EXATAMENTE UMA classe assim no classpath de teste (o
 * cucumber-spring falha se encontrar zero ou mais de uma).
 *
 * <h2>Testcontainers - padrão "Singleton Container"</h2>
 * <p>O container de MySQL é criado e iniciado em um bloco {@code static},
 * então roda apenas UMA VEZ para todo o processo da JVM de teste (todos os
 * cenários de todas as *.feature compartilham o mesmo banco) - é o padrão
 * "Singleton Container", recomendado pela própria documentação do
 * Testcontainers para suítes com muitos testes, evitando o custo (vários
 * segundos) de subir um container novo a cada cenário. A consequência direta
 * é que os dados de UM cenário continuam visíveis para os cenários
 * seguintes - por isso cada cenário gera seus próprios identificadores
 * únicos (protocolo, externalId, transactionId...) em vez de contar com um
 * banco "limpo" a cada execução (ver {@code UniqueTestData}, na mesma
 * suíte) - a mesma estratégia que a ferramenta em shell script já usava.
 *
 * <p>A imagem "mysql:8.4" é a MESMA usada em desenvolvimento local (ver
 * README, seção "Como rodar o projeto"), incluindo a flag
 * "--log-bin-trust-function-creators=1", necessária para o Flyway conseguir
 * rodar a migration V3 (criação de triggers de auditoria) com binary log
 * habilitado - sem essa flag, a aplicação nem sobe (mesmo erro documentado
 * no README para quem sobe o MySQL manualmente).
 *
 * <p><b>Nota de versão (18/09/2026)</b>: o Testcontainers 2.x (a major
 * version atual, usada aqui - ver {@code testcontainers.version} no
 * {@code pom.xml}) removeu os parâmetros de tipo genérico "auto-referente"
 * que a versão 1.x exigia (antes seria {@code MySQLContainer<?>}/
 * {@code new MySQLContainer<>(...)}) - as classes de container agora são
 * tipos concretos "crus", o que na prática só significa menos ruído de
 * generics no código, sem mudar nada do comportamento abaixo. O pacote
 * também mudou, de {@code org.testcontainers.containers.MySQLContainer}
 * para {@code org.testcontainers.mysql.MySQLContainer} (módulos de banco de
 * dados específicos ganharam pacote/artefato próprio nesta major version).
 */
@CucumberContextConfiguration
@SpringBootTest(webEnvironment = RANDOM_PORT)
public class SpringIntegrationConfig {

    private static final MySQLContainer MYSQL_CONTAINER =
            new MySQLContainer(DockerImageName.parse("mysql:8.4"))
                    .withDatabaseName("billing_db")
                    .withUsername("billing_app")
                    .withPassword("billing_app")
                    .withCommand("--log-bin-trust-function-creators=1");

    static {
        MYSQL_CONTAINER.start();
    }

    /**
     * Sobrescreve, em tempo de execução, as propriedades de conexão de banco
     * que o {@code application.yml} normalmente pegaria de variáveis de
     * ambiente ({@code DB_URL}/{@code DB_USERNAME}/{@code DB_PASSWORD}) -
     * apontando-as para o container Testcontainers em vez de um MySQL fixo
     * em {@code localhost:3306}. O Flyway roda normalmente na subida do
     * contexto, contra este banco efêmero, exatamente como rodaria em
     * qualquer outro ambiente.
     */
    @DynamicPropertySource
    static void configureDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL_CONTAINER::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL_CONTAINER::getUsername);
        registry.add("spring.datasource.password", MYSQL_CONTAINER::getPassword);
    }

    @LocalServerPort
    private int port;

    /**
     * Configura o REST Assured para apontar para a porta ALEATÓRIA em que o
     * Tomcat embutido subiu (ver {@code webEnvironment = RANDOM_PORT} acima -
     * evita conflito com uma porta 8080 fixa, que poderia já estar em uso
     * pela aplicação "de verdade" rodando localmente).
     *
     * <p>O {@code @PostConstruct} roda a cada cenário (o cucumber-spring cria
     * uma instância nova desta classe por cenário, seguindo o mesmo ciclo de
     * vida usado pelas classes de step definitions), mas isso é inofensivo:
     * a porta não muda entre cenários (é o MESMO contexto Spring/Tomcat
     * reaproveitado), então é apenas uma reatribuição redundante, não um
     * novo bind de porta.
     */
    @PostConstruct
    void configureRestAssured() {
        RestAssured.port = port;
        RestAssured.baseURI = "http://localhost";
    }
}
