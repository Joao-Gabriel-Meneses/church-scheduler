package br.igreja.escala.escala.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.igreja.escala.AcessoDeTeste;
import br.igreja.escala.compartilhado.NaoEncontradoException;
import br.igreja.escala.compartilhado.RegraVioladaException;
import br.igreja.escala.compartilhado.domain.AcaoAuditada;
import br.igreja.escala.compartilhado.domain.RegistroDeAuditoria;
import br.igreja.escala.compartilhado.service.AuditoriaService;
import br.igreja.escala.escala.domain.LimitePorPeriodoParams;
import br.igreja.escala.escala.domain.MaxPorNivelParams;
import br.igreja.escala.escala.domain.Regra;
import br.igreja.escala.escala.domain.TipoDeRegra;
import br.igreja.escala.escala.repository.RegraRepository;
import br.igreja.escala.ministerio.Exemplos;
import br.igreja.escala.ministerio.service.MinisterioCriado;
import br.igreja.escala.ministerio.service.MinisterioService;
import br.igreja.escala.ministerio.service.NivelService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class RegraServiceTest {

    private static final long MIDIA = AcessoDeTeste.MIDIA;
    private static final long GERENTE = AcessoDeTeste.GERENTE_DA_MIDIA.getId();

    private final RegraRepository regras = mock(RegraRepository.class);
    private final MinisterioService ministerios = mock(MinisterioService.class);
    private final NivelService niveis = mock(NivelService.class);
    private final AuditoriaService auditoria = mock(AuditoriaService.class);
    private final RegraService servico = new RegraService(regras, ministerios, niveis, auditoria);

    private final Regra limite = new Regra(MIDIA, TipoDeRegra.LIMITE_POR_PERIODO);
    private final Regra maximo = new Regra(MIDIA, TipoDeRegra.MAX_POR_NIVEL_NO_EVENTO);

    @BeforeEach
    void prepara() {
        var midia = Exemplos.midia();
        when(ministerios.buscar(MIDIA)).thenReturn(midia);
        when(ministerios.buscar(9L)).thenThrow(new NaoEncontradoException("Ministério 9"));
        when(niveis.listar(MIDIA)).thenReturn(List.of(Exemplos.iniciante(midia), Exemplos.experiente(midia)));
        when(niveis.buscar(MIDIA, 200L)).thenReturn(Exemplos.iniciante(midia));
        when(niveis.buscar(MIDIA, 900L)).thenThrow(new NaoEncontradoException("Nível 900"));
        when(regras.findByMinisterioId(MIDIA)).thenReturn(List.of(limite, maximo));
        when(regras.findByMinisterioIdAndTipo(MIDIA, TipoDeRegra.LIMITE_POR_PERIODO))
                .thenReturn(Optional.of(limite));
        when(regras.findByMinisterioIdAndTipo(MIDIA, TipoDeRegra.MAX_POR_NIVEL_NO_EVENTO))
                .thenReturn(Optional.of(maximo));
        when(regras.save(any())).thenAnswer(chamada -> chamada.getArgument(0));
    }

    @Test
    void ministerioNovoRecebeOCatalogoPadrao() {
        servico.aoCriarMinisterio(new MinisterioCriado(3L));

        var gravadas = ArgumentCaptor.forClass(Regra.class);
        verify(regras, times(TipoDeRegra.values().length)).save(gravadas.capture());
        assertThat(gravadas.getAllValues()).extracting(Regra::getTipo).containsExactly(TipoDeRegra.values());
        assertThat(gravadas.getAllValues()).allMatch(regra -> regra.getMinisterioId() == 3L);
    }

    @Test
    void tiposSemLinhaValemOPadrao() {
        when(regras.findByMinisterioId(MIDIA)).thenReturn(List.of());

        var vigentes = servico.doMinisterio(MIDIA);

        assertThat(vigentes.limitePorMes()).isEqualTo(3);
        assertThat(vigentes.todas()).hasSize(TipoDeRegra.values().length);
    }

    @Test
    void maximoPorNivelComNivelExcluidoValeComoDesligado() {
        maximo.alterar(new MaxPorNivelParams(999L, 1), true);

        assertThat(servico.doMinisterio(MIDIA).maximoPorNivel()).isEmpty();
        assertThat(servico.resumos(MIDIA))
                .filteredOn(resumo -> resumo.codigo().equals("MAX_POR_NIVEL_NO_EVENTO"))
                .singleElement()
                .satisfies(resumo -> {
                    assertThat(resumo.estado()).isEqualTo("Desligada");
                    assertThat(resumo.descricao())
                            .isEqualTo("Sem efeito: o nível escolhido foi excluído. Escolha outro nível.");
                });
    }

    @Test
    void ministerioQueNaoExisteE404() {
        assertThatThrownBy(() -> servico.doMinisterio(9L)).isInstanceOf(NaoEncontradoException.class);
        assertThatThrownBy(() -> servico.alterarLimite(9L, 4, AcessoDeTeste.GERENTE_DA_MIDIA))
                .isInstanceOf(NaoEncontradoException.class);
    }

    @Test
    void alteraOLimiteEAudita() {
        assertThat(servico.alterarLimite(MIDIA, 4, AcessoDeTeste.GERENTE_DA_MIDIA))
                .isTrue();

        assertThat(limite.getParametros()).isEqualTo(new LimitePorPeriodoParams(4));
        verify(auditoria)
                .registrar(new RegistroDeAuditoria(
                        AcaoAuditada.ALTERAR_REGRA,
                        GERENTE,
                        MIDIA,
                        null,
                        "Limite do mês: de 3 para 4 eventos por pessoa (Mídia)."));
    }

    @Test
    void mesmoLimiteNaoMudaNemAudita() {
        assertThat(servico.alterarLimite(MIDIA, 3, AcessoDeTeste.GERENTE_DA_MIDIA))
                .isFalse();

        verify(auditoria, never()).registrar(any());
    }

    @Test
    void limiteForaDoIntervaloERecusadoNoCampo() {
        for (int invalido : new int[] {0, 32}) {
            assertThatThrownBy(() -> servico.alterarLimite(MIDIA, invalido, AcessoDeTeste.GERENTE_DA_MIDIA))
                    .isInstanceOfSatisfying(RegraVioladaException.class, recusa -> {
                        assertThat(recusa.campo()).isEqualTo("maximo");
                        assertThat(recusa.getMessage()).isEqualTo("O limite vai de 1 a 31 eventos no mês.");
                    });
        }
        verify(auditoria, never()).registrar(any());
    }

    @Test
    void ministerioSemALinhaGanhaAoAlterar() {
        when(regras.findByMinisterioIdAndTipo(MIDIA, TipoDeRegra.LIMITE_POR_PERIODO))
                .thenReturn(Optional.empty());

        servico.alterarLimite(MIDIA, 2, AcessoDeTeste.GERENTE_DA_MIDIA);

        var gravada = ArgumentCaptor.forClass(Regra.class);
        verify(regras).save(gravada.capture());
        assertThat(gravada.getValue().getParametros()).isEqualTo(new LimitePorPeriodoParams(2));
    }

    @Test
    void ligaOMaximoPorNivelEAudita() {
        assertThat(servico.alterarMaximoPorNivel(MIDIA, true, 200L, 1, AcessoDeTeste.GERENTE_DA_MIDIA))
                .isTrue();

        assertThat(servico.doMinisterio(MIDIA).maximoPorNivel()).contains(new MaxPorNivelParams(200L, 1));
        verify(auditoria)
                .registrar(new RegistroDeAuditoria(
                        AcaoAuditada.ALTERAR_REGRA,
                        GERENTE,
                        MIDIA,
                        null,
                        "Máximo por nível ligado: 1 pessoa do nível Iniciante por evento (Mídia)."));
        assertThat(servico.resumos(MIDIA))
                .filteredOn(resumo -> resumo.codigo().equals("MAX_POR_NIVEL_NO_EVENTO"))
                .singleElement()
                .satisfies(resumo -> {
                    assertThat(resumo.estado()).isEqualTo("Ligada");
                    assertThat(resumo.descricao()).isEqualTo("No máximo 1 pessoa do nível Iniciante por evento.");
                });
    }

    @Test
    void desligaOMaximoPorNivel() {
        maximo.alterar(new MaxPorNivelParams(200L, 1), true);

        assertThat(servico.alterarMaximoPorNivel(MIDIA, false, 200L, 1, AcessoDeTeste.GERENTE_DA_MIDIA))
                .isTrue();

        assertThat(maximo.isAtiva()).isFalse();
        verify(auditoria)
                .registrar(new RegistroDeAuditoria(
                        AcaoAuditada.ALTERAR_REGRA, GERENTE, MIDIA, null, "Máximo por nível desligado (Mídia)."));
    }

    @Test
    void ligarSemNivelERecusadoNoCampoENivelDeOutroMinisterioE404() {
        assertThatThrownBy(() -> servico.alterarMaximoPorNivel(MIDIA, true, null, 1, AcessoDeTeste.GERENTE_DA_MIDIA))
                .isInstanceOfSatisfying(RegraVioladaException.class, recusa -> {
                    assertThat(recusa.campo()).isEqualTo("nivelId");
                    assertThat(recusa.getMessage()).isEqualTo("Escolha o nível.");
                });
        assertThatThrownBy(() -> servico.alterarMaximoPorNivel(MIDIA, true, 900L, 1, AcessoDeTeste.GERENTE_DA_MIDIA))
                .isInstanceOf(NaoEncontradoException.class);
        assertThatThrownBy(() -> servico.alterarMaximoPorNivel(MIDIA, true, 200L, 21, AcessoDeTeste.GERENTE_DA_MIDIA))
                .isInstanceOfSatisfying(
                        RegraVioladaException.class,
                        recusa -> assertThat(recusa.campo()).isEqualTo("maximo"));
        assertThat(maximo.isAtiva()).isFalse();
        verify(auditoria, never()).registrar(any());
    }

    @Test
    void resumosNaOrdemDoCatalogoComOQueCadaUmaFaz() {
        assertThat(servico.resumos(MIDIA))
                .extracting(RegraResumo::codigo, RegraResumo::rigidez, RegraResumo::estado, RegraResumo::edicao)
                .containsExactly(
                        tuple("PESSOAS_POR_FUNCAO", "Rígida", "Sempre ativa", null),
                        tuple("HABILITACAO", "Rígida", "Sempre ativa", null),
                        tuple("DISPONIBILIDADE", "Rígida", "Sempre ativa", null),
                        tuple("UMA_FUNCAO_POR_EVENTO", "Rígida", "Sempre ativa", null),
                        tuple("SEM_SOBREPOSICAO", "Rígida", "Sempre ativa", null),
                        tuple("LIMITE_POR_PERIODO", "Rígida", "Ligada", "limite"),
                        tuple("MAX_POR_NIVEL_NO_EVENTO", "Rígida", "Desligada", "maximo-por-nivel"),
                        tuple("PRIORIDADE_POR_DATA", "Prioridade", "Sempre ativa", null),
                        tuple("EQUILIBRIO_DE_CARGA", "Preferência", "Ligada", null));
        assertThat(servico.resumos(MIDIA))
                .filteredOn(resumo -> resumo.codigo().equals("LIMITE_POR_PERIODO"))
                .singleElement()
                .extracting(RegraResumo::descricao)
                .isEqualTo("Cada pessoa serve em no máximo 3 eventos no mês; dois cultos no mesmo dia contam dois.");
    }
}
