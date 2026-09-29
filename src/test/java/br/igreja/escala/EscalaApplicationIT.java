package br.igreja.escala;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManagerFactory;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@TesteDeIntegracao
class EscalaApplicationIT {

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    EntityManagerFactory entityManagerFactory;

    @Test
    void sobeOContextoConectadoAoOracle() {
        assertThat(jdbc.queryForObject("select 1 from dual", Integer.class)).isEqualTo(1);
    }

    /** Os testes rodam no Oracle 23ai, mas o Hibernate precisa gerar SQL e tipos de 19c, como em produção. */
    @Test
    void usaODialetoDoOracle19MesmoNoOracle23() {
        var dialeto = entityManagerFactory
                .unwrap(SessionFactoryImplementor.class)
                .getJdbcServices()
                .getDialect();

        assertThat(dialeto.getVersion().getDatabaseMajorVersion()).isEqualTo(19);
    }
}
