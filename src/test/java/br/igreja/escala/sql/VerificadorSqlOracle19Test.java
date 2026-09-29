package br.igreja.escala.sql;

import static org.assertj.core.api.Assertions.assertThat;

import br.igreja.escala.sql.VerificadorSqlOracle19.Violacao;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class VerificadorSqlOracle19Test {

    @ParameterizedTest(name = "[{0}] {1}")
    @CsvSource(delimiter = '|', textBlock = """
            BOOLEAN                    | create table t (ativo boolean default false not null)
            TRUE/FALSE                 | insert into t (ativo) values (true)
            JSON nativo                | create table t (parametros json)
            JSON nativo                | select json('{}') from dual
            JSON nativo                | select json_query(p, '$.a' returning json) from t
            IF [NOT] EXISTS            | create table if not exists t (id number)
            IF [NOT] EXISTS            | drop table if exists t
            SELECT sem FROM            | select 1
            SELECT sem FROM            | insert into t (criado_em) select sysdate
            VALUES com várias linhas   | insert into t (a, b) values (1, 2), (3, 4)
            VALUES como tabela         | select * from (values (1)) v (a)
            GROUP BY por posição       | select a, count(*) from t group by 1
            GROUP BY por alias         | select extract(month from d) as mes, count(*) from t group by mes
            SQL domain                 | create domain email as varchar2(254)
            annotations                | create table t (id number annotations (display 'Id'))
            VECTOR                     | create table t (v vector(3, float32))
            DEFAULT ON NULL FOR INSERT | create table t (a number default on null for insert and update 0)
            alias de tabela com AS     | select x.a from t as x
            RETURNING OLD/NEW          | update t set a = 1 returning old a into :x
            ON SCHEMA                  | grant select any table on schema app to u
            join direto                | update t set a = u.a from u where t.id = u.id
            join direto                | delete from t from u where t.id = u.id
            DB_DEVELOPER_ROLE          | grant db_developer_role to escala_app
            função do 21c+             | select any_value(a) from t
            """)
    void apontaSintaxeQueNaoExisteNo19c(String regra, String sql) {
        assertThat(VerificadorSqlOracle19.verificar("teste.sql", sql)).isNotEmpty();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "create table t (ativo number(1) default 1 not null, constraint ck check (ativo in (0, 1)))",
                "create table t (parametros clob, constraint ck check (parametros is json))",
                "create table t (p clob check (p is not json))",
                "create search index ix on t (p) for json",
                "select json_object(key 'a' value p format json) from t",
                "select json_value(p, '$.a') from t",
                "select 1 from dual",
                "insert into t (a, b) values (1, 2)",
                "insert into t (a, b) values (to_date('2026-01-01', 'YYYY-MM-DD'), (select 1 from dual))",
                "insert into t (a) select a from u",
                "select a, count(*) from t group by a",
                "select t.nome as nome, count(*) as total from t group by t.nome",
                "select extract(year from d) as ano, trim(leading '0' from c) from t",
                "select * from t as of timestamp systimestamp",
                "update t set a = (select b from u where u.id = t.id)",
                "delete from t where id in (select id from u)",
                "grant select, insert on t to u",
                "create table t (a number default on null 0)",
                "insert into t (texto) values ('drop table if exists; select 1; {\"a\": true}')",
                "select 1 from dual -- if not exists, boolean, select sem from",
                "/* create table t (a boolean) */ select 1 from dual",
                "create table \"BOOLEAN\" (a number)",
            })
    void aceitaSintaxeValidaNo19c(String sql) {
        assertThat(VerificadorSqlOracle19.verificar("teste.sql", sql)).isEmpty();
    }

    @Test
    void permiteBooleanETrueDentroDePlsql() {
        var sql = """
                declare
                    l_ok boolean := false;
                begin
                    dbms_scheduler.create_job(job_name => 'X', job_type => 'PLSQL_BLOCK', enabled => true);
                    select count(*) into l_total from t;
                end;
                /
                """;

        assertThat(VerificadorSqlOracle19.verificar("teste.sql", sql)).isEmpty();
    }

    @Test
    void verificaSqlEmbutidoNoPlsql() {
        var sql = """
                begin
                    insert into t (a) values (1), (2);
                end;
                /
                """;

        assertThat(VerificadorSqlOracle19.verificar("teste.sql", sql))
                .extracting(Violacao::linha)
                .containsExactly(2);
    }

    @Test
    void entendeQQuote() {
        var sql = "select 1 from t where x = q'[if not exists 'boolean']';";

        assertThat(VerificadorSqlOracle19.verificar("teste.sql", sql)).isEmpty();
    }

    @Test
    void informaArquivoLinhaERegra() {
        var sql = """
                create table a (id number);

                create table b (
                    id    number,
                    ativo boolean
                );
                """;

        assertThat(VerificadorSqlOracle19.verificar("V9__x.sql", sql))
                .singleElement()
                .satisfies(v -> {
                    assertThat(v.arquivo()).isEqualTo("V9__x.sql");
                    assertThat(v.linha()).isEqualTo(5);
                    assertThat(v.regra()).contains("BOOLEAN");
                    assertThat(v.trecho()).isEqualTo("ativo boolean");
                    assertThat(v.toString()).startsWith("V9__x.sql:5 [");
                });
    }

    @Test
    void marcadorLiberaFalsoPositivoNaLinha() {
        var sql = "select a from t as x -- " + VerificadorSqlOracle19.MARCADOR;

        assertThat(VerificadorSqlOracle19.verificar("teste.sql", sql)).isEmpty();
    }

    @Test
    void extraiSoOsBlocosSqlDoMarkdownMantendoAsLinhas() {
        var markdown = """
                # Título com "if not exists" no texto

                ```bash
                select 1
                ```

                ```sql
                grant db_developer_role to escala_app;
                ```
                """;

        var violacoes =
                VerificadorSqlOracle19.verificar("deploy.md", VerificadorSqlOracle19.blocosSqlDoMarkdown(markdown));

        assertThat(violacoes).singleElement().satisfies(v -> {
            assertThat(v.linha()).isEqualTo(8);
            assertThat(v.regra()).contains("DB_DEVELOPER_ROLE");
        });
    }
}
