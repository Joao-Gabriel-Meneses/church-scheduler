package br.igreja.escala.ministerio.service;

import static br.igreja.escala.ministerio.Exemplos.experiente;
import static br.igreja.escala.ministerio.Exemplos.iniciante;
import static br.igreja.escala.ministerio.Exemplos.midia;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.igreja.escala.compartilhado.NaoEncontradoException;
import br.igreja.escala.compartilhado.RegraVioladaException;
import br.igreja.escala.ministerio.domain.Ministerio;
import br.igreja.escala.ministerio.repository.HabilitacaoRepository;
import br.igreja.escala.ministerio.repository.NivelRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class NivelServiceTest {

    private final NivelRepository niveis = mock(NivelRepository.class);
    private final HabilitacaoRepository habilitacoes = mock(HabilitacaoRepository.class);
    private final MinisterioService ministerios = mock(MinisterioService.class);
    private final NivelService servico = new NivelService(niveis, habilitacoes, ministerios);

    private final Ministerio midia = midia();

    @Test
    void criaNoMinisterioDaRota() {
        when(ministerios.buscar(1L)).thenReturn(midia);
        when(niveis.findByMinisterioIdAndOrdem(1L, 1)).thenReturn(Optional.empty());
        when(niveis.save(any())).thenAnswer(chamada -> chamada.getArgument(0));

        var criado = servico.criar(1L, new DadosDoNivel("Iniciante", 1));

        assertThat(criado.getMinisterio()).isSameAs(midia);
        assertThat(criado.getOrdem()).isEqualTo(1);
    }

    @Test
    void recusaOrdemDeOutroNivelDizendoQualENomeRepetido() {
        when(ministerios.buscar(1L)).thenReturn(midia);
        when(niveis.findByMinisterioIdAndOrdem(1L, 2)).thenReturn(Optional.of(experiente(midia)));
        when(niveis.existsByMinisterioIdAndNomeIgnoreCase(1L, "Iniciante")).thenReturn(true);

        assertThatThrownBy(() -> servico.criar(1L, new DadosDoNivel("Avançado", 2)))
                .isInstanceOfSatisfying(RegraVioladaException.class, recusa -> {
                    assertThat(recusa.campo()).isEqualTo("ordem");
                    assertThat(recusa.getMessage()).isEqualTo("A ordem 2 já é do nível Experiente.");
                });
        assertThatThrownBy(() -> servico.criar(1L, new DadosDoNivel("Iniciante", 3)))
                .hasMessage("Já existe um nível chamado Iniciante.");
        verify(niveis, never()).save(any());
    }

    @Test
    void alterarMantendoAPropriaOrdemPassa() {
        var experiente = experiente(midia);
        when(niveis.findByIdAndMinisterioId(201L, 1L)).thenReturn(Optional.of(experiente));
        when(niveis.findByMinisterioIdAndOrdem(1L, 2)).thenReturn(Optional.of(experiente));

        servico.alterar(1L, 201L, new DadosDoNivel("Experiente sênior", 2));

        assertThat(experiente.getNome()).isEqualTo("Experiente sênior");
    }

    @Test
    void nivelDeOutroMinisterioNaoEEncontrado() {
        when(niveis.findByIdAndMinisterioId(200L, 2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servico.buscar(2L, 200L)).isInstanceOf(NaoEncontradoException.class);
        assertThatThrownBy(() -> servico.excluir(2L, 200L)).isInstanceOf(NaoEncontradoException.class);
    }

    @Test
    void naoExcluiNivelEmUso() {
        when(niveis.findByIdAndMinisterioId(200L, 1L)).thenReturn(Optional.of(iniciante(midia)));
        when(habilitacoes.countByNivelId(200L)).thenReturn(1L);

        assertThatThrownBy(() -> servico.excluir(1L, 200L))
                .hasMessage("Iniciante não foi excluído: 1 habilitação usa este nível. Mude o nível na página de"
                        + " cada membro e tente de novo.");
        verify(niveis, never()).delete(any());
    }

    @Test
    void excluiNivelSemUso() {
        var iniciante = iniciante(midia);
        when(niveis.findByIdAndMinisterioId(200L, 1L)).thenReturn(Optional.of(iniciante));

        servico.excluir(1L, 200L);

        verify(niveis).delete(iniciante);
    }

    @Test
    void proximaOrdemVemDepoisDoMaisExperiente() {
        when(niveis.findByMinisterioIdOrderByOrdemAsc(1L)).thenReturn(List.of(iniciante(midia), experiente(midia)));
        when(niveis.findByMinisterioIdOrderByOrdemAsc(2L)).thenReturn(List.of());

        assertThat(servico.proximaOrdem(1L)).isEqualTo(3);
        assertThat(servico.proximaOrdem(2L)).isEqualTo(1);
    }

    @Test
    void resumosTrazemAsHabilitacoesDeCadaNivel() {
        when(niveis.findByMinisterioIdOrderByOrdemAsc(1L)).thenReturn(List.of(iniciante(midia)));
        when(habilitacoes.contarPorNivel(1L)).thenReturn(List.of(FuncaoServiceTest.contagem(200L, 4)));

        assertThat(servico.resumos(1L))
                .singleElement()
                .extracting(NivelResumo::descricaoDasHabilitacoes)
                .isEqualTo("4 habilitações");
    }
}
