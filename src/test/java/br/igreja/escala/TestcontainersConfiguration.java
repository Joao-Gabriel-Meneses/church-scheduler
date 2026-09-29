package br.igreja.escala;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.oracle.OracleContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Oracle Free para os testes de integração. Um container novo e vazio por execução (sem reuse).
 *
 * <p>O container é estático: se algum teste criar um segundo contexto Spring, ele reaproveita o mesmo Oracle em vez
 * de subir outro (cada um ocupa cerca de 2 GB). Por isso nenhum teste pode depender de dados deixados por outro;
 * veja {@link TesteDeIntegracao}.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    private static final OracleContainer ORACLE =
            new OracleContainer(DockerImageName.parse("gvenzl/oracle-free:23-slim-faststart"));

    @Bean
    @ServiceConnection
    OracleContainer oracleFreeContainer() {
        return ORACLE;
    }
}
