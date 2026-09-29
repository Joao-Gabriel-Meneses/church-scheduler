package br.igreja.escala;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Confere, no schema que o Flyway acabou de criar, que nada usa recursos que só existem a partir do 21c/23ai.
 * Complementa o {@code VerificadorSqlOracle19} (que lê o texto das migrações): aqui o que conta é o resultado real.
 */
@TesteDeIntegracao
class SchemaCompativelComOracle19IT {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void nenhumaColunaUsaTiposQueNaoExistemNo19c() {
        var colunas = jdbc.queryForList("""
                select table_name || '.' || column_name || ' ' || data_type
                  from user_tab_columns
                 where data_type in ('BOOLEAN', 'JSON', 'VECTOR')
                """, String.class);

        assertThat(colunas).as("colunas com tipos exclusivos do 21c/23ai").isEmpty();
    }

    @Test
    void naoHaSqlDomainsNemAnnotations() {
        assertThat(jdbc.queryForObject("select count(*) from user_domains", Integer.class))
                .as("SQL domains (23ai)")
                .isZero();
        assertThat(jdbc.queryForObject("select count(*) from user_annotations_usage", Integer.class))
                .as("annotations (23ai)")
                .isZero();
    }
}
