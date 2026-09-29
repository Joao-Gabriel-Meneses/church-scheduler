package br.igreja.escala;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@TesteDeIntegracao
class EscalaApplicationIT {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void sobeOContextoConectadoAoOracle() {
        assertThat(jdbc.queryForObject("select 1 from dual", Integer.class)).isEqualTo(1);
    }
}
