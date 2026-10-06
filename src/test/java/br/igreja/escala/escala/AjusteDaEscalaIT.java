package br.igreja.escala.escala;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.igreja.escala.TesteDeIntegracao;
import br.igreja.escala.compartilhado.EdicaoConcorrenteException;
import br.igreja.escala.compartilhado.Fuso;
import br.igreja.escala.disponibilidade.domain.Disponibilidade;
import br.igreja.escala.disponibilidade.domain.Resposta;
import br.igreja.escala.disponibilidade.repository.DisponibilidadeRepository;
import br.igreja.escala.escala.domain.MinPorNivelParams;
import br.igreja.escala.escala.domain.Regra;
import br.igreja.escala.escala.domain.TipoDeRegra;
import br.igreja.escala.escala.domain.Vaga;
import br.igreja.escala.escala.repository.RegraRepository;
import br.igreja.escala.escala.repository.VagaRepository;
import br.igreja.escala.escala.service.AjusteDaEscala;
import br.igreja.escala.escala.service.GeracaoDaEscala;
import br.igreja.escala.evento.domain.Evento;
import br.igreja.escala.evento.domain.Periodo;
import br.igreja.escala.evento.repository.EventoRepository;
import br.igreja.escala.evento.repository.PeriodoRepository;
import br.igreja.escala.identidade.domain.Usuario;
import br.igreja.escala.identidade.domain.UsuarioAutenticado;
import br.igreja.escala.identidade.repository.UsuarioRepository;
import br.igreja.escala.ministerio.domain.CorDoMinisterio;
import br.igreja.escala.ministerio.domain.Funcao;
import br.igreja.escala.ministerio.domain.Habilitacao;
import br.igreja.escala.ministerio.domain.Icone;
import br.igreja.escala.ministerio.domain.Membresia;
import br.igreja.escala.ministerio.domain.Ministerio;
import br.igreja.escala.ministerio.domain.Nivel;
import br.igreja.escala.ministerio.repository.FuncaoRepository;
import br.igreja.escala.ministerio.repository.HabilitacaoRepository;
import br.igreja.escala.ministerio.repository.MembresiaRepository;
import br.igreja.escala.ministerio.repository.MinisterioRepository;
import br.igreja.escala.ministerio.repository.NivelRepository;
import java.time.Duration;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * O ajuste manual de ponta a ponta no Oracle, por POST direto, com transações de verdade (sem {@code @Transactional}):
 * a concorrência só aparece com dois commits, e o ajuste bloqueia o período. Os dados são gravados de verdade e
 * apagados no {@code @AfterEach}.
 *
 * <p>Culto do dia 10, com Projeção, Transmissão e Som, e o mínimo de 1 Experiente por evento. Lucas (Projeção) e Diego
 * (Transmissão) são Iniciantes e já estão escalados; Som está vazia. Felipe é Iniciante em Som, Ana é Experiente em
 * Som, e Bruno é Experiente em Som mas marcou Não pode.
 */
@TesteDeIntegracao
class AjusteDaEscalaIT {

    private static final YearMonth MES = YearMonth.now(Fuso.SAO_PAULO).plusMonths(2);

    @Autowired
    MockMvc mvc;

    @Autowired
    TransactionTemplate transacao;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    UsuarioRepository usuarios;

    @Autowired
    MinisterioRepository ministerios;

    @Autowired
    MembresiaRepository membresias;

    @Autowired
    FuncaoRepository funcoes;

    @Autowired
    NivelRepository niveis;

    @Autowired
    HabilitacaoRepository habilitacoes;

    @Autowired
    PeriodoRepository periodos;

    @Autowired
    EventoRepository eventos;

    @Autowired
    DisponibilidadeRepository disponibilidades;

    @Autowired
    VagaRepository vagas;

    @Autowired
    RegraRepository regras;

    @Autowired
    AjusteDaEscala ajuste;

    @Autowired
    GeracaoDaEscala geracao;

    private final ExecutorService paralelo = Executors.newFixedThreadPool(2);

    private Long midiaId;
    private Long louvorId;
    private Long somId;
    private Long felipeId;
    private Long anaId;
    private Long brunoId;
    private UsuarioAutenticado gerente;
    private UsuarioAutenticado gerenteDoLouvor;
    private UsuarioAutenticado membro;

    @BeforeEach
    void gravaOsDados() {
        transacao.executeWithoutResult(status -> {
            var midia = ministerios.save(new Ministerio("Mídia Ajuste", CorDoMinisterio.MINT, Icone.MONITOR));
            var louvor = ministerios.save(new Ministerio("Louvor Ajuste", CorDoMinisterio.ROSE, Icone.MUSIC));
            midiaId = midia.getId();
            louvorId = louvor.getId();
            var paula = usuarios.save(Usuario.membro("Paula Ajuste", "paula.ajuste@teste.local", "{noop}x"));
            var gil = usuarios.save(Usuario.membro("Gil Ajuste", "gil.ajuste@teste.local", "{noop}x"));
            var lucas = usuarios.save(Usuario.membro("Lucas Ajuste", "lucas.ajuste@teste.local", "{noop}x"));
            var diego = usuarios.save(Usuario.membro("Diego Ajuste", "diego.ajuste@teste.local", "{noop}x"));
            var felipe = usuarios.save(Usuario.membro("Felipe Ajuste", "felipe.ajuste@teste.local", "{noop}x"));
            var ana = usuarios.save(Usuario.membro("Ana Ajuste", "ana.ajuste@teste.local", "{noop}x"));
            var bruno = usuarios.save(Usuario.membro("Bruno Ajuste", "bruno.ajuste@teste.local", "{noop}x"));
            felipeId = felipe.getId();
            anaId = ana.getId();
            brunoId = bruno.getId();
            var daPaula = new Membresia(paula.getId(), midia);
            daPaula.tornarGerente();
            membresias.save(daPaula);
            var doGil = new Membresia(gil.getId(), louvor);
            doGil.tornarGerente();
            membresias.save(doGil);
            for (var pessoa : List.of(lucas, diego, felipe, ana, bruno)) {
                membresias.save(new Membresia(pessoa.getId(), midia));
            }

            var projecao = funcoes.save(new Funcao(midia, "Projeção", Icone.MONITOR, 1, 1));
            var transmissao = funcoes.save(new Funcao(midia, "Transmissão", Icone.VIDEO, 1, 1));
            var som = funcoes.save(new Funcao(midia, "Som", Icone.MIC, 1, 1));
            somId = som.getId();
            var iniciante = niveis.save(new Nivel(midia, "Iniciante", 1));
            var experiente = niveis.save(new Nivel(midia, "Experiente", 2));
            habilitacoes.save(new Habilitacao(lucas.getId(), projecao, iniciante));
            habilitacoes.save(new Habilitacao(diego.getId(), transmissao, iniciante));
            habilitacoes.save(new Habilitacao(felipe.getId(), som, iniciante));
            habilitacoes.save(new Habilitacao(ana.getId(), som, experiente));
            habilitacoes.save(new Habilitacao(bruno.getId(), som, experiente));
            var minimo = new Regra(midiaId, TipoDeRegra.MIN_POR_NIVEL_NO_EVENTO);
            minimo.alterar(new MinPorNivelParams(experiente.getId(), 1), true);
            regras.save(minimo);

            var periodo = new Periodo(midiaId, MES);
            periodo.travarDisponibilidade();
            periodos.save(periodo);
            var dia10 = eventos.save(Evento.avulso(
                    periodo, "Culto de domingo", MES.atDay(10), LocalTime.of(18, 0), Duration.ofHours(2)));
            for (var pessoa : List.of(lucas, diego, felipe, ana)) {
                disponibilidades.save(new Disponibilidade(pessoa.getId(), dia10, Resposta.PODE, pessoa.getId()));
            }
            disponibilidades.save(new Disponibilidade(bruno.getId(), dia10, Resposta.NAO_PODE, bruno.getId()));
            var doLucas = new Vaga(dia10.getId(), projecao.getId(), 1);
            doLucas.escalar(lucas.getId());
            vagas.save(doLucas);
            var doDiego = new Vaga(dia10.getId(), transmissao.getId(), 1);
            doDiego.escalar(diego.getId());
            vagas.save(doDiego);
            vagas.save(new Vaga(dia10.getId(), som.getId(), 1));

            gerente = new UsuarioAutenticado(paula);
            gerenteDoLouvor = new UsuarioAutenticado(gil);
            membro = new UsuarioAutenticado(ana);
        });
    }

    @AfterEach
    void apagaOsDados() throws Exception {
        paralelo.shutdownNow();
        var andamento = geracao.andamento(midiaId, MES);
        if (andamento.isPresent()) {
            andamento.get().getFim().get(30, TimeUnit.SECONDS);
            geracao.retirarTerminada(midiaId, MES);
        }
        transacao.executeWithoutResult(status -> {
            Object[] ids = {midiaId, louvorId};
            String doMinisterio = "(select id from evento where ministerio_id in (?, ?))";
            jdbc.update("delete from vaga where evento_id in " + doMinisterio, ids);
            jdbc.update("delete from disponibilidade where evento_id in " + doMinisterio, ids);
            jdbc.update("delete from evento_funcao where evento_id in " + doMinisterio, ids);
            jdbc.update("delete from auditoria where ministerio_id in (?, ?)", ids);
            jdbc.update("delete from evento where ministerio_id in (?, ?)", ids);
            jdbc.update("delete from periodo where ministerio_id in (?, ?)", ids);
            jdbc.update(
                    "delete from habilitacao where funcao_id in (select id from funcao where ministerio_id in (?, ?))",
                    ids);
            jdbc.update("delete from funcao where ministerio_id in (?, ?)", ids);
            jdbc.update("delete from nivel where ministerio_id in (?, ?)", ids);
            jdbc.update("delete from regra where ministerio_id in (?, ?)", ids);
            jdbc.update("delete from membresia where ministerio_id in (?, ?)", ids);
            jdbc.update("delete from ministerio where id in (?, ?)", ids);
            jdbc.update("delete from usuario where email like '%.ajuste@teste.local'");
        });
    }

    @Test
    void oTerceiroInicianteERecusadoNoServidorMesmoComJustificativa() throws Exception {
        var som = vagaDoSom();

        mvc.perform(escalar(som, felipeId, "Só ele pode").with(user(gerente)))
                .andExpect(flash().attribute("recusa", Matchers.endsWith("Regra: MIN_POR_NIVEL_NO_EVENTO")));

        assertThat(vagaDoSom().isVazia()).isTrue();
        assertThat(auditorias("AJUSTAR_VAGA") + auditorias("FORCAR_VAGA")).isZero();
    }

    @Test
    void doisIniciantesEUmExperienteSaoAceitosEAVagaFicaFixada() throws Exception {
        mvc.perform(escalar(vagaDoSom(), anaId, null).with(user(gerente)))
                .andExpect(flash().attribute("sucesso", Matchers.startsWith("Ana Ajuste está em Som")));

        var som = vagaDoSom();
        assertThat(som.getUsuarioId()).isEqualTo(anaId);
        assertThat(som.isFixada()).isTrue();
        assertThat(som.isForcada()).isFalse();
        assertThat(auditorias("AJUSTAR_VAGA")).isEqualTo(1);
    }

    @Test
    void forcarSemJustificativaERecusadoEComJustificativaFicaForcadoEAuditado() throws Exception {
        mvc.perform(escalar(vagaDoSom(), brunoId, " ").with(user(gerente)))
                .andExpect(flash().attribute("recusa", Matchers.startsWith("Para escalar Bruno Ajuste mesmo assim")));
        assertThat(vagaDoSom().isVazia()).isTrue();

        mvc.perform(escalar(vagaDoSom(), brunoId, "Combinou comigo por telefone")
                        .with(user(gerente)))
                .andExpect(flash().attribute("sucesso", Matchers.endsWith("com a vaga forçada")));

        var som = vagaDoSom();
        assertThat(som.isForcada()).isTrue();
        assertThat(som.isFixada()).isTrue();
        assertThat(som.getJustificativa()).isEqualTo("Combinou comigo por telefone");
        assertThat(jdbc.queryForObject(
                        "select count(*) from auditoria where ministerio_id = ? and acao = 'FORCAR_VAGA'"
                                + " and descricao like '%DISPONIBILIDADE%Combinou comigo por telefone%'",
                        Integer.class, midiaId))
                .isEqualTo(1);
    }

    @Test
    void edicaoComAVersaoVelhaNaoSobrescreveAQueGravouAntes() throws Exception {
        var som = vagaDoSom();
        long versaoDaTela = som.getVersao();

        mvc.perform(escalar(som.getId(), anaId, null, versaoDaTela).with(user(gerente)))
                .andExpect(flash().attribute("sucesso", Matchers.notNullValue()));
        mvc.perform(post("/ministerios/{m}/escalas/vagas/{v}/esvaziar", midiaId, som.getId())
                        .param("versao", String.valueOf(versaoDaTela))
                        .param("mes", MES.toString())
                        .header("HX-Request", "true")
                        .with(csrf())
                        .with(user(gerente)))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("A vaga mudou em outra aba")));

        assertThat(vagaDoSom().getUsuarioId()).as("a primeira continua gravada").isEqualTo(anaId);
    }

    /**
     * Duas abas tocam a mesma vaga ao mesmo tempo, com a mesma versão, em transações de verdade: o período bloqueado
     * enfileira as duas, uma grava e a outra encontra a versão nova e é recusada.
     */
    @Test
    void duasEdicoesAoMesmoTempoUmaGravaEAOutraERecusada() throws Exception {
        var som = vagaDoSom();
        long versao = som.getVersao();
        var largada = new CyclicBarrier(2);
        List<Future<String>> toques = new ArrayList<>();
        for (Long quem : List.of(anaId, brunoId)) {
            String justificativa = quem.equals(brunoId) ? "Combinou comigo" : null;
            toques.add(paralelo.submit(() -> {
                largada.await(10, TimeUnit.SECONDS);
                try {
                    ajuste.escalar(midiaId, som.getId(), quem, justificativa, versao, gerente);
                    return "gravou";
                } catch (EdicaoConcorrenteException | ObjectOptimisticLockingFailureException recusada) {
                    return "recusada";
                }
            }));
        }
        var resultados = new ArrayList<String>();
        for (var toque : toques) {
            resultados.add(toque.get(30, TimeUnit.SECONDS));
        }

        assertThat(resultados).containsExactlyInAnyOrder("gravou", "recusada");
        assertThat(vagaDoSom().getVersao()).isEqualTo(versao + 1);
    }

    @Test
    void escalaPublicadaNaoAceitaGeracaoSemReabrir() throws Exception {
        mvc.perform(post("/ministerios/{m}/escalas/publicar", midiaId)
                        .param("mes", MES.toString())
                        .with(csrf())
                        .with(user(gerente)))
                .andExpect(flash().attribute("sucesso", Matchers.startsWith("Escala de")));

        mvc.perform(gerar().with(user(gerente)))
                .andExpect(flash().attribute("recusa", Matchers.containsString("está publicada")));
        assertThat(auditorias("GERAR_ESCALA")).isZero();

        mvc.perform(post("/ministerios/{m}/escalas/reabrir", midiaId)
                        .param("mes", MES.toString())
                        .with(csrf())
                        .with(user(gerente)))
                .andExpect(flash().attribute("sucesso", Matchers.endsWith("reaberta para rascunho")));
        mvc.perform(gerar().with(user(gerente))).andExpect(flash().attributeCount(0));
        assertThat(auditorias("PUBLICAR_ESCALA")).isEqualTo(1);
        assertThat(auditorias("REABRIR_ESCALA")).isEqualTo(1);
    }

    @Test
    void gerenteDeOutroMinisterioEMembroNaoMexemNaVagaNemPublicam() throws Exception {
        var som = vagaDoSom();
        for (var quem : List.of(gerenteDoLouvor, membro)) {
            mvc.perform(escalar(som, anaId, null).with(user(quem))).andExpect(status().isForbidden());
            mvc.perform(post("/ministerios/{m}/escalas/publicar", midiaId)
                            .param("mes", MES.toString())
                            .with(csrf())
                            .with(user(quem)))
                    .andExpect(status().isForbidden());
            mvc.perform(post("/ministerios/{m}/escalas/reabrir", midiaId)
                            .param("mes", MES.toString())
                            .with(csrf())
                            .with(user(quem)))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(post("/ministerios/{m}/escalas/vagas/{v}/esvaziar", louvorId, som.getId())
                        .param("versao", "0")
                        .param("mes", MES.toString())
                        .with(csrf())
                        .with(user(gerenteDoLouvor)))
                .andExpect(status().isNotFound());

        assertThat(vagaDoSom().isVazia()).isTrue();
        assertThat(jdbc.queryForObject(
                        "select status_escala from periodo where ministerio_id = ?", String.class, midiaId))
                .isEqualTo("RASCUNHO");
    }

    private Vaga vagaDoSom() {
        return vagas.findAll().stream()
                .filter(vaga -> vaga.getFuncaoId().equals(somId))
                .findFirst()
                .orElseThrow();
    }

    private MockHttpServletRequestBuilder escalar(Vaga vaga, Long usuarioId, String justificativa) {
        return escalar(vaga.getId(), usuarioId, justificativa, vaga.getVersao());
    }

    private MockHttpServletRequestBuilder escalar(Long vagaId, Long usuarioId, String justificativa, long versao) {
        var requisicao = post("/ministerios/{m}/escalas/vagas/{v}/escalar", midiaId, vagaId)
                .param("usuarioId", String.valueOf(usuarioId))
                .param("versao", String.valueOf(versao))
                .param("mes", MES.toString())
                .with(csrf());
        return justificativa == null ? requisicao : requisicao.param("justificativa", justificativa);
    }

    private MockHttpServletRequestBuilder gerar() {
        return post("/ministerios/{m}/escalas/gerar", midiaId)
                .param("mes", MES.toString())
                .with(csrf());
    }

    private int auditorias(String acao) {
        return jdbc.queryForObject(
                "select count(*) from auditoria where ministerio_id = ? and acao = ?", Integer.class, midiaId, acao);
    }
}
