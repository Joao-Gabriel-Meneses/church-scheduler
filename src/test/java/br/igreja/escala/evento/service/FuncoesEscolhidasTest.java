package br.igreja.escala.evento.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.igreja.escala.compartilhado.NaoEncontradoException;
import br.igreja.escala.compartilhado.RegraVioladaException;
import java.util.List;
import org.junit.jupiter.api.Test;

class FuncoesEscolhidasTest {

    private static final List<Long> PROJECAO_E_TRANSMISSAO = List.of(100L, 101L);

    @Test
    void todasMarcadasGravaVazioQueEOPadrao() {
        assertThat(FuncoesEscolhidas.paraGravar(List.of(101L, 100L), PROJECAO_E_TRANSMISSAO))
                .isEmpty();
    }

    @Test
    void parteMarcadaGravaSoElas() {
        assertThat(FuncoesEscolhidas.paraGravar(List.of(100L, 100L), PROJECAO_E_TRANSMISSAO))
                .containsExactly(100L);
    }

    @Test
    void nenhumaMarcadaERecusadaNoCampo() {
        assertThatThrownBy(() -> FuncoesEscolhidas.paraGravar(List.of(), PROJECAO_E_TRANSMISSAO))
                .isInstanceOfSatisfying(RegraVioladaException.class, recusa -> {
                    assertThat(recusa.campo()).isEqualTo("funcoes");
                    assertThat(recusa.getMessage()).isEqualTo("Escolha ao menos uma função.");
                });
    }

    @Test
    void ministerioSemFuncoesAceitaNenhuma() {
        assertThat(FuncoesEscolhidas.paraGravar(List.of(), List.of())).isEmpty();
    }

    @Test
    void funcaoDeOutroMinisterioE404() {
        assertThatThrownBy(() -> FuncoesEscolhidas.paraGravar(List.of(100L, 900L), PROJECAO_E_TRANSMISSAO))
                .isInstanceOf(NaoEncontradoException.class);
    }
}
