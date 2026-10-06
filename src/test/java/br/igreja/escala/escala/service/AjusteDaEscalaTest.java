package br.igreja.escala.escala.service;

import static br.igreja.escala.evento.ExemplosDeEvento.DUAS_HORAS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ai.timefold.solver.core.api.solver.SolutionManager;
import ai.timefold.solver.core.api.solver.SolverFactory;
import ai.timefold.solver.core.config.solver.SolverConfig;
import br.igreja.escala.AcessoDeTeste;
import br.igreja.escala.compartilhado.EdicaoConcorrenteException;
import br.igreja.escala.compartilhado.NaoEncontradoException;
import br.igreja.escala.compartilhado.RegraVioladaException;
import br.igreja.escala.compartilhado.domain.AcaoAuditada;
import br.igreja.escala.compartilhado.domain.RegistroDeAuditoria;
import br.igreja.escala.compartilhado.service.AuditoriaService;
import br.igreja.escala.escala.domain.MinPorNivelParams;
import br.igreja.escala.escala.domain.RegraVigente;
import br.igreja.escala.escala.domain.RegrasDoMinisterio;
import br.igreja.escala.escala.domain.Rigidez;
import br.igreja.escala.escala.domain.TipoDeRegra;
import br.igreja.escala.escala.domain.Vaga;
import br.igreja.escala.escala.repository.VagaRepository;
import br.igreja.escala.escala.solver.EscalaDoPeriodo;
import br.igreja.escala.escala.solver.RestricoesDaEscala;
import br.igreja.escala.escala.solver.VagaPlanejada;
import br.igreja.escala.evento.ExemplosDeEvento;
import br.igreja.escala.evento.domain.Evento;
import br.igreja.escala.evento.domain.Periodo;
import br.igreja.escala.evento.service.EventoService;
import br.igreja.escala.evento.service.PeriodoService;
import br.igreja.escala.identidade.service.UsuarioResumo;
import br.igreja.escala.ministerio.Exemplos;
import br.igreja.escala.ministerio.domain.Funcao;
import br.igreja.escala.ministerio.domain.Icone;
import br.igreja.escala.ministerio.service.HabilitacaoDaPessoa;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * O ajuste manual com o solver de verdade na validação. Outubro de 2026, hoje é 07/10: o culto do dia 4 já passou, o
 * do dia 11 tem Projeção, Transmissão e Som, e o mínimo é 1 Experiente por evento.
 */
class AjusteDaEscalaTest {

    private static final long MIDIA = AcessoDeTeste.MIDIA;
    private static final long GERENTE = AcessoDeTeste.GERENTE_DA_MIDIA.getId();
    private static final YearMonth OUTUBRO = YearMonth.of(2026, 10);

    private final LeituraDoPeriodo leitura = mock(LeituraDoPeriodo.class);
    private final VagaRepository vagas = mock(VagaRepository.class);
    private final EventoService eventos = mock(EventoService.class);
    private final PeriodoService periodos = mock(PeriodoService.class);
    private final GeracoesEmAndamento andamentos = new GeracoesEmAndamento();
    private final AuditoriaService auditoria = mock(AuditoriaService.class);
    private final AjusteDaEscala ajuste = new AjusteDaEscala(
            leitura,
            vagas,
            eventos,
            periodos,
            andamentos,
            SolutionManager.create(SolverFactory.create(new SolverConfig()
                    .withSolutionClass(EscalaDoPeriodo.class)
                    .withEntityClasses(VagaPlanejada.class)
                    .withConstraintProviderClass(RestricoesDaEscala.class))),
            auditoria,
            mock(EntityManager.class));

    private final Periodo outubro = ExemplosDeEvento.comId(ExemplosDeEvento.periodo(MIDIA, OUTUBRO), 400L);
    private final Funcao projecao = Exemplos.projecao(Exemplos.midia());
    private final Funcao transmissao = Exemplos.transmissao(Exemplos.midia());
    private final Funcao som = Exemplos.comId(new Funcao(Exemplos.midia(), "Som", Icone.MIC, 1, 1), 102L);

    private final Evento passado = evento(500L, 4);
    private final Evento domingo = evento(501L, 11);

    private final UsuarioResumo ana = pessoa(30L, "Ana Souza");
    private final UsuarioResumo bruno = pessoa(31L, "Bruno Alves");
    private final UsuarioResumo carla = pessoa(32L, "Carla Dias");
    private final UsuarioResumo diego = pessoa(33L, "Diego Martins");
    private final UsuarioResumo felipe = pessoa(34L, "Felipe Nunes");

    private final List<Vaga> doMes = new ArrayList<>();

    @BeforeEach
    void prepara() {
        ReflectionTestUtils.setField(outubro, "disponibilidadeTravada", true);
        when(eventos.buscar(MIDIA, passado.getId())).thenReturn(passado);
        when(eventos.buscar(MIDIA, domingo.getId())).thenReturn(domingo);
        when(eventos.buscar(AcessoDeTeste.LOUVOR, domingo.getId())).thenThrow(new NaoEncontradoException("Evento 501"));
        when(periodos.doMes(MIDIA, OUTUBRO)).thenReturn(Optional.of(outubro));
        when(periodos.bloquearParaAlterar(400L)).thenReturn(outubro);
        when(leitura.ler(MIDIA, OUTUBRO)).thenAnswer(chamada -> dados());
    }

    @Test
    void quemPassaEmTudoEntraNaVagaQueFicaFixadaEAuditada() {
        vaga(1L, domingo, projecao, ana);
        var vazia = vaga(2L, domingo, transmissao, null);

        String aviso = ajuste.escalar(MIDIA, 2L, diego.id(), "ignorada", 0, AcessoDeTeste.GERENTE_DA_MIDIA);

        assertThat(aviso).isEqualTo("Diego Martins está em Transmissão, 11/10 · Dom");
        assertThat(vazia.getUsuarioId()).isEqualTo(diego.id());
        assertThat(vazia.isFixada()).isTrue();
        assertThat(vazia.isForcada()).isFalse();
        assertThat(vazia.getJustificativa()).isNull();
        verify(auditoria)
                .registrar(new RegistroDeAuditoria(
                        AcaoAuditada.AJUSTAR_VAGA,
                        GERENTE,
                        MIDIA,
                        diego.id(),
                        "Transmissão, 11/10 · Dom · 18h00 · Culto 11 (Mídia): vazia → Diego Martins."));
    }

    @Nested
    class Forcar {

        @Test
        void semJustificativaERecusadoNoCampoESemMudarNada() {
            vaga(1L, domingo, projecao, ana);
            var vazia = vaga(2L, domingo, transmissao, null);

            assertThatThrownBy(() -> ajuste.escalar(MIDIA, 2L, carla.id(), "  ", 0, AcessoDeTeste.GERENTE_DA_MIDIA))
                    .isInstanceOfSatisfying(RegraVioladaException.class, recusa -> {
                        assertThat(recusa.campo()).isEqualTo("justificativa");
                        assertThat(recusa.getMessage())
                                .isEqualTo("Para escalar Carla Dias mesmo assim, escreva a justificativa. Não marcou"
                                        + " Pode neste evento. Regra: DISPONIBILIDADE");
                    });
            assertThat(vazia.isVazia()).isTrue();
            verify(auditoria, never()).registrar(any());
        }

        @Test
        void comJustificativaForcaFixaEAuditaARegra() {
            vaga(1L, domingo, projecao, ana);
            var vazia = vaga(2L, domingo, transmissao, null);

            String aviso = ajuste.escalar(
                    MIDIA, 2L, carla.id(), "Combinou comigo por telefone", 0, AcessoDeTeste.GERENTE_DA_MIDIA);

            assertThat(aviso).endsWith(", com a vaga forçada");
            assertThat(vazia.isForcada()).isTrue();
            assertThat(vazia.isFixada()).isTrue();
            assertThat(vazia.getJustificativa()).isEqualTo("Combinou comigo por telefone");
            verify(auditoria)
                    .registrar(new RegistroDeAuditoria(
                            AcaoAuditada.FORCAR_VAGA,
                            GERENTE,
                            MIDIA,
                            carla.id(),
                            "Transmissão, 11/10 · Dom · 18h00 · Culto 11 (Mídia): vazia → Carla Dias. Forçada"
                                    + " (DISPONIBILIDADE): Combinou comigo por telefone"));
        }

        @Test
        void regraQueNuncaSeForcaERecusadaMesmoComJustificativa() {
            vaga(1L, domingo, projecao, ana);
            var vazia = vaga(2L, domingo, transmissao, null);

            assertThatThrownBy(() ->
                            ajuste.escalar(MIDIA, 2L, ana.id(), "Preciso dela", 0, AcessoDeTeste.GERENTE_DA_MIDIA))
                    .isInstanceOfSatisfying(RegraVioladaException.class, recusa -> {
                        assertThat(recusa.campo()).isNull();
                        assertThat(recusa.getMessage())
                                .isEqualTo("Ana Souza não pode servir em Transmissão, 11/10 · Dom · 18h00 · Culto 11."
                                        + " Já serve em Projeção neste evento. Regra: UMA_FUNCAO_POR_EVENTO");
                    });
            assertThat(vazia.isVazia()).isTrue();
            verify(auditoria, never()).registrar(any());
        }

        @Test
        void justificativaLongaERecusadaNoCampo() {
            vaga(1L, domingo, projecao, ana);
            vaga(2L, domingo, transmissao, null);

            assertThatThrownBy(() ->
                            ajuste.escalar(MIDIA, 2L, carla.id(), "x".repeat(501), 0, AcessoDeTeste.GERENTE_DA_MIDIA))
                    .isInstanceOfSatisfying(
                            RegraVioladaException.class,
                            recusa -> assertThat(recusa.campo()).isEqualTo("justificativa"));
        }
    }

    @Nested
    class MinimoPorNivel {

        @Test
        void oTerceiroInicianteERecusadoMesmoComJustificativa() {
            vaga(1L, domingo, projecao, bruno);
            vaga(2L, domingo, transmissao, diego);
            var vagaDoSom = vaga(3L, domingo, som, null);

            assertThatThrownBy(
                            () -> ajuste.escalar(MIDIA, 3L, felipe.id(), "Só ele", 0, AcessoDeTeste.GERENTE_DA_MIDIA))
                    .isInstanceOfSatisfying(
                            RegraVioladaException.class,
                            recusa -> assertThat(recusa.getMessage())
                                    .startsWith("Felipe Nunes não pode servir em Som")
                                    .endsWith("Regra: MIN_POR_NIVEL_NO_EVENTO"));
            assertThat(vagaDoSom.isVazia()).isTrue();
        }

        @Test
        void doisIniciantesEUmExperienteSaoAceitos() {
            vaga(1L, domingo, projecao, bruno);
            vaga(2L, domingo, transmissao, diego);
            var vagaDoSom = vaga(3L, domingo, som, null);

            ajuste.escalar(MIDIA, 3L, ana.id(), null, 0, AcessoDeTeste.GERENTE_DA_MIDIA);

            assertThat(vagaDoSom.getUsuarioId()).isEqualTo(ana.id());
            assertThat(vagaDoSom.isForcada()).isFalse();
        }

        @Test
        void tirarOUnicoExperienteERecusado() {
            var daAna = vaga(1L, domingo, projecao, ana);
            vaga(2L, domingo, transmissao, diego);

            assertThatThrownBy(() -> ajuste.esvaziar(MIDIA, 1L, 0, AcessoDeTeste.GERENTE_DA_MIDIA))
                    .isInstanceOf(RegraVioladaException.class)
                    .hasMessageEndingWith("Regra: MIN_POR_NIVEL_NO_EVENTO");
            assertThat(daAna.getUsuarioId()).isEqualTo(ana.id());
        }
    }

    @Test
    void esvaziarFixaVaziaEAudita() {
        vaga(1L, domingo, projecao, ana);
        var doDiego = vaga(2L, domingo, transmissao, diego);

        assertThat(ajuste.esvaziar(MIDIA, 2L, 0, AcessoDeTeste.GERENTE_DA_MIDIA))
                .isEqualTo("Vaga de Transmissão, 11/10 · Dom esvaziada");

        assertThat(doDiego.isVazia()).isTrue();
        assertThat(doDiego.isFixada()).isTrue();
        verify(auditoria)
                .registrar(new RegistroDeAuditoria(
                        AcaoAuditada.AJUSTAR_VAGA,
                        GERENTE,
                        MIDIA,
                        diego.id(),
                        "Transmissão, 11/10 · Dom · 18h00 · Culto 11 (Mídia): Diego Martins → vazia."));
    }

    @Test
    void fixarEDesafixarAuditamSoQuandoMudam() {
        var daAna = vaga(1L, domingo, projecao, ana);

        ajuste.fixar(MIDIA, 1L, 0, AcessoDeTeste.GERENTE_DA_MIDIA);
        ajuste.fixar(MIDIA, 1L, 0, AcessoDeTeste.GERENTE_DA_MIDIA);
        assertThat(daAna.isFixada()).isTrue();
        ajuste.desafixar(MIDIA, 1L, 0, AcessoDeTeste.GERENTE_DA_MIDIA);
        assertThat(daAna.isPresa()).isFalse();

        verify(auditoria)
                .registrar(new RegistroDeAuditoria(
                        AcaoAuditada.FIXAR_VAGA,
                        GERENTE,
                        MIDIA,
                        ana.id(),
                        "Projeção, 11/10 · Dom · 18h00 · Culto 11 (Mídia): vaga fixada com Ana Souza."));
        verify(auditoria)
                .registrar(new RegistroDeAuditoria(
                        AcaoAuditada.DESAFIXAR_VAGA,
                        GERENTE,
                        MIDIA,
                        ana.id(),
                        "Projeção, 11/10 · Dom · 18h00 · Culto 11 (Mídia): vaga solta com Ana Souza."));
    }

    @Test
    void naEscalaPublicadaOAjusteValeEAAuditoriaDiz() {
        outubro.publicarEscala();
        vaga(1L, domingo, projecao, ana);
        vaga(2L, domingo, transmissao, null);

        ajuste.escalar(MIDIA, 2L, diego.id(), null, 0, AcessoDeTeste.GERENTE_DA_MIDIA);

        verify(auditoria)
                .registrar(new RegistroDeAuditoria(
                        AcaoAuditada.AJUSTAR_VAGA,
                        GERENTE,
                        MIDIA,
                        diego.id(),
                        "Transmissão, 11/10 · Dom · 18h00 · Culto 11 (Mídia, escala publicada): vazia → Diego"
                                + " Martins."));
    }

    @Test
    void versaoVelhaERecusadaSemMudarNada() {
        vaga(1L, domingo, projecao, ana);
        var vazia = vaga(2L, domingo, transmissao, null);
        ReflectionTestUtils.setField(vazia, "versao", 3L);

        assertThatThrownBy(() -> ajuste.escalar(MIDIA, 2L, diego.id(), null, 2, AcessoDeTeste.GERENTE_DA_MIDIA))
                .isInstanceOf(EdicaoConcorrenteException.class);
        assertThat(vazia.isVazia()).isTrue();
        verify(auditoria, never()).registrar(any());
    }

    @Test
    void comAGeracaoRodandoNaoSeAjusta() {
        vaga(1L, domingo, projecao, ana);
        var vazia = vaga(2L, domingo, transmissao, null);
        andamentos.registrar(new Andamento(400L, MIDIA, OUTUBRO, GERENTE, Instant.now()));

        assertThatThrownBy(() -> ajuste.escalar(MIDIA, 2L, diego.id(), null, 0, AcessoDeTeste.GERENTE_DA_MIDIA))
                .isInstanceOf(RegraVioladaException.class)
                .hasMessage("A escala de outubro está sendo gerada. Espere terminar para ajustar as vagas.");
        assertThat(vazia.isVazia()).isTrue();
    }

    @Test
    void eventoQueJaComecouNaoSeAjusta() {
        var antiga = vaga(9L, passado, projecao, ana);

        assertThatThrownBy(() -> ajuste.esvaziar(MIDIA, 9L, 0, AcessoDeTeste.GERENTE_DA_MIDIA))
                .isInstanceOf(RegraVioladaException.class)
                .hasMessageStartingWith("Esta vaga não se ajusta mais");
        assertThat(antiga.getUsuarioId()).isEqualTo(ana.id());
    }

    @Test
    void vagaDeOutroMinisterioEPessoaQueNaoServeSao404() {
        vaga(1L, domingo, projecao, ana);
        vaga(2L, domingo, transmissao, null);

        assertThatThrownBy(() -> ajuste.esvaziar(AcessoDeTeste.LOUVOR, 1L, 0, AcessoDeTeste.GERENTE_DA_MIDIA))
                .isInstanceOf(NaoEncontradoException.class);
        assertThatThrownBy(() -> ajuste.escalar(MIDIA, 2L, 99L, null, 0, AcessoDeTeste.GERENTE_DA_MIDIA))
                .isInstanceOf(NaoEncontradoException.class);
        assertThatThrownBy(() -> ajuste.abrir(MIDIA, 77L)).isInstanceOf(NaoEncontradoException.class);
    }

    @Test
    void candidatosDaFuncaoComQuemPassaEmTudoPrimeiro() {
        vaga(1L, domingo, projecao, ana);
        vaga(2L, domingo, transmissao, null);

        var aberta = ajuste.abrir(MIDIA, 2L);

        assertThat(aberta.titulo()).isEqualTo("Transmissão, 11/10 · Dom · 18h00");
        assertThat(aberta.isEditavel()).isTrue();
        assertThat(aberta.isVazia()).isTrue();
        assertThat(aberta.motivoDaVazia()).isNotBlank();
        assertThat(aberta.candidatos())
                .extracting(Candidato::nome, Candidato::isLivre, Candidato::isForcavel, Candidato::isBloqueado)
                .containsExactly(
                        tuple("Diego Martins", true, false, false),
                        tuple("Carla Dias", false, true, false),
                        tuple("Ana Souza", false, false, true));
        assertThat(aberta.candidatos().get(2).porQue()).isEqualTo("Já serve em Projeção neste evento.");
        assertThat(aberta.candidatos().getFirst().descricao()).isEqualTo("Iniciante · nenhuma escala no mês");
    }

    private Vaga vaga(Long id, Evento evento, Funcao funcao, UsuarioResumo pessoa) {
        var vaga = ExemplosDeEvento.comId(new Vaga(evento.getId(), funcao.getId(), 1), id);
        vaga.escalar(pessoa == null ? null : pessoa.id());
        when(vagas.findById(id)).thenReturn(Optional.of(vaga));
        doMes.add(vaga);
        return vaga;
    }

    private DadosDoPeriodo dados() {
        return new DadosDoPeriodo(
                MIDIA,
                "Mídia",
                OUTUBRO,
                outubro,
                List.of(passado, domingo),
                List.of(projecao, transmissao, som),
                List.copyOf(doMes),
                List.of(ana, bruno, carla, diego, felipe),
                List.of(),
                Map.of(domingo.getId(), Set.of(ana.id(), bruno.id(), diego.id(), felipe.id())),
                List.of(
                        new HabilitacaoDaPessoa(ana.id(), projecao.getId(), 201L),
                        new HabilitacaoDaPessoa(ana.id(), transmissao.getId(), 201L),
                        new HabilitacaoDaPessoa(ana.id(), som.getId(), 201L),
                        new HabilitacaoDaPessoa(bruno.id(), projecao.getId(), 200L),
                        new HabilitacaoDaPessoa(carla.id(), transmissao.getId(), 201L),
                        new HabilitacaoDaPessoa(diego.id(), transmissao.getId(), 200L),
                        new HabilitacaoDaPessoa(felipe.id(), som.getId(), 200L)),
                Map.of(200L, "Iniciante", 201L, "Experiente"),
                RegrasDoMinisterio.de(List.of(new RegraVigente(
                        TipoDeRegra.MIN_POR_NIVEL_NO_EVENTO, Rigidez.HARD, 1, true, new MinPorNivelParams(201L, 1)))),
                List.of(),
                LocalDateTime.of(2026, 10, 7, 10, 0),
                List.of());
    }

    private Evento evento(Long id, int dia) {
        return ExemplosDeEvento.comId(
                Evento.avulso(outubro, "Culto " + dia, LocalDate.of(2026, 10, dia), LocalTime.of(18, 0), DUAS_HORAS),
                id);
    }

    private static UsuarioResumo pessoa(Long id, String nome) {
        return new UsuarioResumo(id, nome, id + "@teste.local", null, false, false, true);
    }
}
