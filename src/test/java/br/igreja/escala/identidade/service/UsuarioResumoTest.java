package br.igreja.escala.identidade.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class UsuarioResumoTest {

    @Test
    void iniciaisSaoDoPrimeiroEDoUltimoNome() {
        assertThat(resumo("Ana Souza").iniciais()).isEqualTo("AS");
        assertThat(resumo("ana maria de souza").iniciais()).isEqualTo("AS");
        assertThat(resumo("Lucas").iniciais()).isEqualTo("L");
        assertThat(resumo("  Élcio   Lima ").iniciais()).isEqualTo("ÉL");
    }

    @Test
    void primeiroNome() {
        assertThat(resumo("Ana Maria Souza").primeiroNome()).isEqualTo("Ana");
        assertThat(resumo("Lucas").primeiroNome()).isEqualTo("Lucas");
    }

    private static UsuarioResumo resumo(String nome) {
        return new UsuarioResumo(1L, nome, "x@x.com", null, false, false);
    }
}
