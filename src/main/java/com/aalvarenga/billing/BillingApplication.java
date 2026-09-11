package com.aalvarenga.billing;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Classe de entrada (bootstrap) da aplicação Spring Boot.
 *
 * <p>A anotação {@link SpringBootApplication} é, na prática, um atalho para
 * três outras anotações combinadas:
 * <ul>
 *   <li>{@code @Configuration} - permite que esta classe declare beans;</li>
 *   <li>{@code @EnableAutoConfiguration} - liga a "mágica" do Spring Boot que
 *       configura automaticamente Tomcat, Jackson, Hibernate, etc. com base
 *       nas dependências presentes no classpath (o "starter" que colocamos
 *       no pom.xml);</li>
 *   <li>{@code @ComponentScan} - varre o pacote atual (com.aalvarenga.billing)
 *       e todos os subpacotes procurando por classes anotadas com
 *       {@code @Component}, {@code @Service}, {@code @Repository},
 *       {@code @RestController} etc., registrando-as automaticamente no
 *       contexto de injeção de dependências.</li>
 * </ul>
 */
@SpringBootApplication
@ConfigurationPropertiesScan // habilita o uso de @ConfigurationProperties em qualquer subpacote (ver config.BillingProperties)
public class BillingApplication {

    public static void main(String[] args) {
        SpringApplication.run(BillingApplication.class, args);
    }
}
