package br.igreja.escala.ministerio.service;

import static br.igreja.escala.ministerio.Exemplos.experiente;
import static br.igreja.escala.ministerio.Exemplos.iniciante;
import static br.igreja.escala.ministerio.Exemplos.midia;
import static br.igreja.escala.ministerio.Exemplos.projecao;
import static br.igreja.escala.ministerio.Exemplos.transmissao;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.igreja.escala.compartilhado.NaoEncontradoException;
import br.igreja.escala.ministerio.domain.Funcao;
import br.igreja.escala.ministerio.domain.Habilitacao;
import br.igreja.escala.ministerio.domain.Icone;
import br.igreja.escala.ministerio.domain.Ministerio;
import br.igreja.escala.ministerio.domain.Nivel;
import br.igreja.escala.ministerio.repository.FuncaoRepository;
import br.igreja.escala.ministerio.repository.HabilitacaoRepository;
import br.igreja.escala.ministerio.repository.MembresiaRepository;
import br.igreja.escala.ministerio.repository.NivelRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class HabilitacaoServiceTest {

    private final HabilitacaoRepository habilitacoes = mock(HabilitacaoRepository.class);
    private final FuncaoRepository funcoes = mock(FuncaoRepository.class);
    private final NivelRepository niveis = mock(NivelRepository.class);
    private final MembresiaRepository membresias = mock(MembresiaRepository.class);
    private final HabilitacaoService servico = new HabilitacaoService(habilitacoes, funcoes, niveis, membresias);

    private final Ministerio midia = midia();
    private final Funcao projecao = projecao(midia);
    private final Funcao transmissao = transmissao(midia);
    private final Nivel iniciante = iniciante(midia);
    private final Nivel experiente = experiente(midia);

    @BeforeEach
    void prepara() {
        when(membresias.existsByUsuarioIdAndMinisterioId(30L, 1L)).thenReturn(true);
        when(funcoes.findByMinisterioIdOrderByNomeAsc(1L)).thenReturn(List.of(projecao, transmissao));
        when(niveis.findByIdAndMinisterioId(200L, 1L)).thenReturn(Optional.of(iniciante));
        when(niveis.findByIdAndMinisterioId(201L, 1L)).thenReturn(Optional.of(experiente));
    }

    @Test
    void umaLinhaPorFuncaoComONivelAtual() {
        when(habilitacoes.findByUsuarioIdAndFuncaoMinisterioId(30L, 1L))
                .thenReturn(List.of(new Habilitacao(30L, transmissao, experiente)));

        assertThat(servico.doMembro(1L, 30L))
                .containsExactly(
                        new LinhaDeHabilitacao(100L, "Projeção", Icone.MONITOR, null),
                        new LinhaDeHabilitacao(101L, "Transmissão", Icone.VIDEO, 201L));
        assertThat(servico.doMembro(1L, 30L).get(0).campo()).isEqualTo("nivel-100");
    }

    @Test
    void criaMudaETiraHabilitacoesDeUmaVez() {
        var naTransmissao = new Habilitacao(30L, transmissao, iniciante);
        when(habilitacoes.findByUsuarioIdAndFuncaoId(30L, 100L)).thenReturn(Optional.empty());
        when(habilitacoes.findByUsuarioIdAndFuncaoId(30L, 101L)).thenReturn(Optional.of(naTransmissao));

        servico.definir(1L, 30L, Map.of(100L, 200L, 101L, 201L));

        var criada = ArgumentCaptor.forClass(Habilitacao.class);
        verify(habilitacoes).save(criada.capture());
        assertThat(criada.getValue().getFuncao()).isSameAs(projecao);
        assertThat(criada.getValue().getNivel()).isSameAs(iniciante);
        assertThat(naTransmissao.getNivel()).isSameAs(experiente);
    }

    @Test
    void funcaoSemNivelOuForaDoMapaPerdeAHabilitacao() {
        var naProjecao = new Habilitacao(30L, projecao, iniciante);
        var naTransmissao = new Habilitacao(30L, transmissao, iniciante);
        when(habilitacoes.findByUsuarioIdAndFuncaoId(30L, 100L)).thenReturn(Optional.of(naProjecao));
        when(habilitacoes.findByUsuarioIdAndFuncaoId(30L, 101L)).thenReturn(Optional.of(naTransmissao));
        var semNivel = new HashMap<Long, Long>();
        semNivel.put(100L, null);

        servico.definir(1L, 30L, semNivel);

        verify(habilitacoes).delete(naProjecao);
        verify(habilitacoes).delete(naTransmissao);
        verify(habilitacoes, never()).save(any());
    }

    @Test
    void nivelDeOutroMinisterioEFuncaoDeOutroMinisterioSao404() {
        when(habilitacoes.findByUsuarioIdAndFuncaoId(any(), any())).thenReturn(Optional.empty());
        when(niveis.findByIdAndMinisterioId(999L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servico.definir(1L, 30L, Map.of(100L, 999L)))
                .isInstanceOf(NaoEncontradoException.class);
        assertThatThrownBy(() -> servico.definir(1L, 30L, Map.of(555L, 200L)))
                .isInstanceOf(NaoEncontradoException.class)
                .hasMessageContaining("555");
        verify(habilitacoes, never()).save(any());
    }

    @Test
    void quemNaoEDoMinisterioNaoGanhaHabilitacao() {
        assertThatThrownBy(() -> servico.definir(1L, 99L, Map.of(100L, 200L)))
                .isInstanceOf(NaoEncontradoException.class);
        verify(habilitacoes, never()).save(any());
    }
}
