package br.igreja.escala;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.oracle.OracleContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Oracle Free 23ai para os testes de integração (*IT), a mesma versão do Autonomous DB de produção.
 *
 * <p>Um único container é compartilhado por todos os contextos de teste: cada Oracle ocupa cerca de 2 GB e
 * dois ao mesmo tempo não cabem na memória da máquina nem do runner do CI.
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
