package br.igreja.escala.ministerio.service;

import static br.igreja.escala.ministerio.Exemplos.midia;
import static br.igreja.escala.ministerio.Exemplos.projecao;
import static br.igreja.escala.ministerio.Exemplos.transmissao;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.igreja.escala.compartilhado.NaoEncontradoException;
import br.igreja.escala.compartilhado.RegraVioladaException;
import br.igreja.escala.ministerio.domain.Icone;
import br.igreja.escala.ministerio.domain.Ministerio;
import br.igreja.escala.ministerio.repository.Contagem;
import br.igreja.escala.ministerio.repository.FuncaoRepository;
import br.igreja.escala.ministerio.repository.HabilitacaoRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class FuncaoServiceTest {

    private final FuncaoRepository funcoes = mock(FuncaoRepository.class);
    private final HabilitacaoRepository habilitacoes = mock(HabilitacaoRepository.class);
    private final MinisterioService ministerios = mock(MinisterioService.class);
    private final FuncaoService servico = new FuncaoService(funcoes, habilitacoes, ministerios);

    private final Ministerio midia = midia();

    @Test
    void criaNoMinisterioDaRota() {
        when(ministerios.buscar(1L)).thenReturn(midia);
        when(funcoes.save(any())).thenAnswer(chamada -> chamada.getArgument(0));

        var criada = servico.criar(1L, new DadosDaFuncao("Projeção", Icone.MONITOR, 1, 1));

        assertThat(criada.getMinisterio()).isSameAs(midia);
        assertThat(criada.getNome()).isEqualTo("Projeção");
    }

    @Test
    void recusaMaximoMenorQueOMinimoNoCampoDoMaximo() {
        when(ministerios.buscar(1L)).thenReturn(midia);

        assertThatThrownBy(() -> servico.criar(1L, new DadosDaFuncao("Projeção", Icone.MONITOR, 2, 1)))
                .isInstanceOfSatisfying(
                        RegraVioladaException.class,
                        recusa -> assertThat(recusa.campo()).isEqualTo("qtdMax"));
        verify(funcoes, never()).save(any());
    }

    @Test
    void recusaNomeRepetidoNoMinisterio() {
        when(ministerios.buscar(1L)).thenReturn(midia);
        when(funcoes.existsByMinisterioIdAndNomeIgnoreCase(1L, "Projeção")).thenReturn(true);

        assertThatThrownBy(() -> servico.criar(1L, new DadosDaFuncao(" Projeção", Icone.MONITOR, 1, 1)))
                .hasMessage("Já existe uma função chamada Projeção.");
    }

    @Test
    void funcaoDeOutroMinisterioNaoEEncontrada() {
        when(funcoes.findByIdAndMinisterioId(100L, 2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servico.buscar(2L, 100L)).isInstanceOf(NaoEncontradoException.class);
        assertThatThrownBy(() -> servico.alterar(2L, 100L, new DadosDaFuncao("X", Icone.MONITOR, 1, 1)))
                .isInstanceOf(NaoEncontradoException.class);
        assertThatThrownBy(() -> servico.excluir(2L, 100L)).isInstanceOf(NaoEncontradoException.class);
    }

    @Test
    void alteraAFuncaoDoMinisterio() {
        var projecao = projecao(midia);
        when(funcoes.findByIdAndMinisterioId(100L, 1L)).thenReturn(Optional.of(projecao));

        servico.alterar(1L, 100L, new DadosDaFuncao("Projeção", Icone.MONITOR, 1, 2));

        assertThat(projecao.getQtdMax()).isEqualTo(2);
    }

    @Test
    void naoExcluiFuncaoComMembrosHabilitados() {
        var projecao = projecao(midia);
        when(funcoes.findByIdAndMinisterioId(100L, 1L)).thenReturn(Optional.of(projecao));
        when(habilitacoes.countByFuncaoId(100L)).thenReturn(3L);

        assertThatThrownBy(() -> servico.excluir(1L, 100L))
                .isInstanceOfSatisfying(RegraVioladaException.class, recusa -> {
                    assertThat(recusa.campo()).isNull();
                    assertThat(recusa.getMessage())
                            .isEqualTo("Projeção não foi excluída: 3 membros estão habilitados nela. Tire a"
                                    + " habilitação na página de cada membro e tente de novo.");
                });
        verify(funcoes, never()).delete(any());
    }

    @Test
    void excluiFuncaoSemHabilitados() {
        var projecao = projecao(midia);
        when(funcoes.findByIdAndMinisterioId(100L, 1L)).thenReturn(Optional.of(projecao));

        assertThat(servico.excluir(1L, 100L)).isSameAs(projecao);
        verify(funcoes).delete(projecao);
    }

    @Test
    void resumosTrazemQuantosEstaoHabilitados() {
        when(funcoes.findByMinisterioIdOrderByNomeAsc(1L)).thenReturn(List.of(projecao(midia), transmissao(midia)));
        when(habilitacoes.contarPorFuncao(1L)).thenReturn(List.of(contagem(100L, 1)));

        assertThat(servico.resumos(1L))
                .extracting(FuncaoResumo::nome, FuncaoResumo::descricaoDosHabilitados)
                .containsExactly(tuple("Projeção", "1 habilitado"), tuple("Transmissão", "0 habilitados"));
    }

    @Test
    void pessoasPorEventoEmPortugues() {
        assertThat(resumo(1, 1).pessoasPorEvento()).isEqualTo("1 pessoa por evento");
        assertThat(resumo(2, 2).pessoasPorEvento()).isEqualTo("2 pessoas por evento");
        assertThat(resumo(0, 1).pessoasPorEvento()).isEqualTo("Até 1 pessoa por evento");
        assertThat(resumo(1, 3).pessoasPorEvento()).isEqualTo("De 1 a 3 pessoas por evento");
    }

    private static FuncaoResumo resumo(int minimo, int maximo) {
        return new FuncaoResumo(1L, "Projeção", Icone.MONITOR, minimo, maximo, 0);
    }

    static Contagem contagem(Long id, long total) {
        return new Contagem() {
            @Override
            public Long getId() {
                return id;
            }

            @Override
            public long getTotal() {
                return total;
            }
        };
    }
}
