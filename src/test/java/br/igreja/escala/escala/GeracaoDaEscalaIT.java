package br.igreja.escala.escala;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.igreja.escala.TesteDeIntegracao;
import br.igreja.escala.compartilhado.Datas;
import br.igreja.escala.compartilhado.Fuso;
import br.igreja.escala.disponibilidade.domain.Disponibilidade;
import br.igreja.escala.disponibilidade.domain.Resposta;
import br.igreja.escala.disponibilidade.repository.DisponibilidadeRepository;
import br.igreja.escala.escala.domain.Vaga;
import br.igreja.escala.escala.repository.VagaRepository;
import br.igreja.escala.escala.service.Andamento;
import br.igreja.escala.escala.service.GeracaoDaEscala;
import br.igreja.escala.evento.domain.Evento;
import br.igreja.escala.evento.domain.Periodo;
import br.igreja.escala.evento.repository.EventoRepository;
import br.igreja.escala.evento.repository.PeriodoRepository;
import br.igreja.escala.evento.service.EventoService;
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
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * A geração de ponta a ponta no Oracle, com o solver de verdade (terminação curta, do @TesteDeIntegracao). Sem
 * {@code @Transactional}: a geração roda na thread do solver e só enxerga o que foi commitado, então os dados são
 * gravados de verdade e apagados no {@code @AfterEach}, como no DisponibilidadeConcorrenciaIT.
 *
 * <p>Na Mídia, a Ana é a única habilitada em Transmissão; no dia 10 ela já canta no Louvor das 19h00 às 20h30, que se
 * sobrepõe ao culto das 18h00 às 20h00.
 */
@TesteDeIntegracao
class GeracaoDaEscalaIT {

    private static final YearMonth MES = YearMonth.now(Fuso.SAO_PAULO).plusMonths(2);
    private static final Duration DUAS_HORAS = Duration.ofHours(2);

    @Autowired
    MockMvc mvc;

    @Autowired
    TransactionTemplate transacao;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    GeracaoDaEscala geracao;

    @Autowired
    EventoService eventoService;

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

    private Long midiaId;
    private Long louvorId;
    private Long periodoId;
    private Long domingo10;
    private Long domingo17;
    private Long projecaoId;
    private Long transmissaoId;
    private Long anaId;
    private Long carlaId;
    private Long brunoId;
    private Long ensaioId;
    private Long periodoDoLouvorId;
    private UsuarioAutenticado gerente;
    private UsuarioAutenticado membro;

    @BeforeEach
    void gravaOsDados() {
        transacao.executeWithoutResult(status -> {
            var midia = ministerios.save(new Ministerio("Mídia Geração", CorDoMinisterio.MINT, Icone.MONITOR));
            var louvor = ministerios.save(new Ministerio("Louvor Geração", CorDoMinisterio.ROSE, Icone.MUSIC));
            midiaId = midia.getId();
            louvorId = louvor.getId();
            var paula = usuarios.save(Usuario.membro("Paula Geração", "paula.geracao@teste.local", "{noop}x"));
            var ana = usuarios.save(Usuario.membro("Ana Geração", "ana.geracao@teste.local", "{noop}x"));
            var bruno = usuarios.save(Usuario.membro("Bruno Geração", "bruno.geracao@teste.local", "{noop}x"));
            var carla = usuarios.save(Usuario.membro("Carla Geração", "carla.geracao@teste.local", "{noop}x"));
            anaId = ana.getId();
            carlaId = carla.getId();
            brunoId = bruno.getId();
            var daPaula = new Membresia(paula.getId(), midia);
            daPaula.tornarGerente();
            membresias.save(daPaula);
            for (var pessoa : List.of(ana, bruno, carla)) {
                membresias.save(new Membresia(pessoa.getId(), midia));
            }
            membresias.save(new Membresia(ana.getId(), louvor));

            var projecao = funcoes.save(new Funcao(midia, "Projeção", Icone.MONITOR, 1, 1));
            var transmissao = funcoes.save(new Funcao(midia, "Transmissão", Icone.VIDEO, 1, 1));
            var vocal = funcoes.save(new Funcao(louvor, "Vocal", Icone.MIC, 1, 1));
            projecaoId = projecao.getId();
            transmissaoId = transmissao.getId();
            var experiente = niveis.save(new Nivel(midia, "Experiente", 1));
            var voz = niveis.save(new Nivel(louvor, "Experiente", 1));
            habilitacoes.save(new Habilitacao(bruno.getId(), projecao, experiente));
            habilitacoes.save(new Habilitacao(carla.getId(), projecao, experiente));
            habilitacoes.save(new Habilitacao(ana.getId(), transmissao, experiente));
            habilitacoes.save(new Habilitacao(ana.getId(), vocal, voz));

            var periodo = new Periodo(midiaId, MES);
            periodo.travarDisponibilidade();
            periodoId = periodos.save(periodo).getId();
            var doLouvor = periodos.save(new Periodo(louvorId, MES));
            var dia10 = eventos.save(
                    Evento.avulso(periodo, "Culto de domingo", MES.atDay(10), LocalTime.of(18, 0), DUAS_HORAS));
            var dia17 = eventos.save(
                    Evento.avulso(periodo, "Culto de domingo", MES.atDay(17), LocalTime.of(18, 0), DUAS_HORAS));
            domingo10 = dia10.getId();
            domingo17 = dia17.getId();
            var ensaio = eventos.save(Evento.avulso(
                    doLouvor, "Ensaio do louvor", MES.atDay(10), LocalTime.of(19, 0), Duration.ofMinutes(90)));
            ensaioId = ensaio.getId();
            periodoDoLouvorId = doLouvor.getId();
            disponibilidades.save(new Disponibilidade(ana.getId(), ensaio, Resposta.PODE, ana.getId()));
            var cantando = new Vaga(ensaio.getId(), vocal.getId(), 1);
            cantando.escalar(ana.getId());
            vagas.save(cantando);
            for (var pessoa : List.of(ana, bruno, carla)) {
                for (var evento : List.of(dia10, dia17)) {
                    disponibilidades.save(new Disponibilidade(pessoa.getId(), evento, Resposta.PODE, pessoa.getId()));
                }
            }
            gerente = new UsuarioAutenticado(paula);
            membro = new UsuarioAutenticado(ana);
        });
    }

    @AfterEach
    void apagaOsDados() throws Exception {
        for (Long ministerioId : List.of(midiaId, louvorId)) {
            var andamento = geracao.andamento(ministerioId, MES);
            if (andamento.isPresent()) {
                andamento.get().getFim().get(30, TimeUnit.SECONDS);
            }
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
            jdbc.update("delete from usuario where email like '%.geracao@teste.local'");
        });
    }

    @Test
    void geraAEscalaSemPorNinguemNoHorarioEmQueJaServeNoLouvor() throws Exception {
        var andamento = gerar();

        assertThat(andamento.getEstado()).isEqualTo(Andamento.Estado.CONCLUIDA);
        assertThat(pessoa(domingo10, projecaoId)).isNotNull().isNotEqualTo(anaId);
        assertThat(pessoa(domingo10, transmissaoId))
                .as("a Ana canta no Louvor nesse horário")
                .isNull();
        assertThat(pessoa(domingo17, transmissaoId)).isEqualTo(anaId);
        assertThat(jdbc.queryForObject(
                        "select count(*) from auditoria where ministerio_id = ? and acao = 'GERAR_ESCALA'"
                                + " and autor_id = ? and descricao like '%3 de 4 vagas preenchidas%'",
                        Integer.class, midiaId, gerente.getId()))
                .isEqualTo(1);

        String pagina = paginaDaEscala();
        assertThat(pagina)
                .contains("Escala de " + nomeDoMes() + " gerada: 3 de 4 vagas preenchidas")
                .contains("Transmissão, " + Datas.dataCurta(MES.atDay(10)) + " · 18h00 · Culto de domingo")
                .contains("A única pessoa que pode já serve em outro evento nesse horário.")
                .contains("Regra: SEM_SOBREPOSICAO")
                .doesNotContain("Louvor Geração", "Ensaio do louvor", "Vocal");
    }

    @Test
    void gerarSemTravaERecusadoNoServidor() throws Exception {
        jdbc.update("update periodo set disponibilidade_travada = 0 where id = ?", periodoId);

        mvc.perform(post("/ministerios/{m}/escalas/gerar", midiaId)
                        .param("mes", MES.toString())
                        .with(csrf())
                        .with(user(gerente)))
                .andExpect(flash().attribute("recusa", Matchers.startsWith("Trave a disponibilidade")));

        assertThat(geracao.andamento(midiaId, MES)).isEmpty();
        assertThat(vagas.findByEventoIdIn(List.of(domingo10, domingo17))).isEmpty();
    }

    @Test
    void gerarDeNovoMantemAVagaFixada() throws Exception {
        gerar();
        var vaga = vagas.findByEventoIdIn(List.of(domingo17)).stream()
                .filter(cada -> cada.getFuncaoId().equals(projecaoId))
                .findFirst()
                .orElseThrow();
        jdbc.update("update vaga set usuario_id = ?, fixada = 1 where id = ?", carlaId, vaga.getId());

        assertThat(gerar().getEstado()).isEqualTo(Andamento.Estado.CONCLUIDA);

        assertThat(pessoa(domingo17, projecaoId)).isEqualTo(carlaId);
    }

    /**
     * Pelas telas: a Carla entra à mão na Projeção do dia 17 (fixada) e o Bruno é forçado na Projeção do dia 10 depois
     * de marcar Não pode, com justificativa. "Regerar não fixadas" não mexe nas duas.
     */
    @Test
    void vagaAjustadaAMaoEVagaForcadaSobrevivemAGerarDeNovo() throws Exception {
        gerar();
        var projecao17 = vaga(domingo17, projecaoId);
        mvc.perform(post("/ministerios/{m}/escalas/vagas/{v}/escalar", midiaId, projecao17.getId())
                        .param("usuarioId", String.valueOf(carlaId))
                        .param("versao", String.valueOf(projecao17.getVersao()))
                        .param("mes", MES.toString())
                        .with(csrf())
                        .with(user(gerente)))
                .andExpect(flash().attribute("sucesso", Matchers.notNullValue()));
        jdbc.update(
                "update disponibilidade set resposta = 'NAO_PODE' where usuario_id = ? and evento_id = ?",
                brunoId,
                domingo10);
        var projecao10 = vaga(domingo10, projecaoId);
        mvc.perform(post("/ministerios/{m}/escalas/vagas/{v}/escalar", midiaId, projecao10.getId())
                        .param("usuarioId", String.valueOf(brunoId))
                        .param("justificativa", "Combinou comigo por telefone")
                        .param("versao", String.valueOf(projecao10.getVersao()))
                        .param("mes", MES.toString())
                        .with(csrf())
                        .with(user(gerente)))
                .andExpect(flash().attribute("sucesso", Matchers.endsWith("com a vaga forçada")));

        assertThat(gerar().getEstado()).isEqualTo(Andamento.Estado.CONCLUIDA);

        var fixada = vaga(domingo17, projecaoId);
        assertThat(fixada.getUsuarioId()).isEqualTo(carlaId);
        assertThat(fixada.isFixada()).isTrue();
        var forcada = vaga(domingo10, projecaoId);
        assertThat(forcada.getUsuarioId()).isEqualTo(brunoId);
        assertThat(forcada.isForcada()).isTrue();
        assertThat(forcada.getJustificativa()).isEqualTo("Combinou comigo por telefone");
    }

    @Test
    void eventoCanceladoSaiDaEscalaEAProximaGeracaoApagaAsVagas() throws Exception {
        gerar();
        transacao.executeWithoutResult(status -> eventoService.cancelar(midiaId, domingo17));

        assertThat(paginaDaEscala())
                .contains(Datas.dataCurta(MES.atDay(17)) + " · 18h00 · Culto de domingo foi cancelado")
                .contains("Ana Geração (Transmissão)");

        gerar();
        assertThat(vagas.findByEventoIdIn(List.of(domingo17))).isEmpty();
        assertThat(paginaDaEscala()).doesNotContain("foi cancelado");
    }

    /**
     * As duas gerações disputam a Ana no dia 10, em horários que se sobrepõem. Na fila, uma termina de gravar antes de a
     * outra ler o banco, então ela fica em um ministério só.
     */
    @Test
    void duasGeracoesAoMesmoTempoNaoPoemAMesmaPessoaEmDoisMinisteriosNoMesmoHorario() throws Exception {
        jdbc.update("delete from vaga where evento_id = ?", ensaioId);
        jdbc.update("update periodo set disponibilidade_travada = 1 where id = ?", periodoDoLouvorId);

        var daMidia = geracao.iniciar(midiaId, MES, gerente);
        var doLouvor = geracao.iniciar(louvorId, MES, gerente);
        daMidia.getFim().get(30, TimeUnit.SECONDS);
        doLouvor.getFim().get(30, TimeUnit.SECONDS);

        boolean naMidia = anaId.equals(pessoa(domingo10, transmissaoId));
        boolean noLouvor =
                vagas.findByEventoIdIn(List.of(ensaioId)).stream().anyMatch(vaga -> anaId.equals(vaga.getUsuarioId()));
        assertThat(naMidia ^ noLouvor)
                .as("a Ana em um ministério só (Mídia: %s, Louvor: %s)", naMidia, noLouvor)
                .isTrue();
        geracao.retirarTerminada(louvorId, MES);
    }

    @Test
    void soOGerenteDoMinisterioVeEGeraAEscala() throws Exception {
        mvc.perform(get("/ministerios/{m}/escalas", midiaId).with(user(membro))).andExpect(status().isForbidden());
        mvc.perform(get("/ministerios/{m}/escalas", louvorId).with(user(gerente)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/ministerios/{m}/escalas/gerar", louvorId)
                        .param("mes", MES.toString())
                        .with(csrf())
                        .with(user(gerente)))
                .andExpect(status().isForbidden());
        assertThat(geracao.andamento(louvorId, MES)).isEmpty();
    }

    private Andamento gerar() throws Exception {
        mvc.perform(post("/ministerios/{m}/escalas/gerar", midiaId)
                        .param("mes", MES.toString())
                        .with(csrf())
                        .with(user(gerente)))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeCount(0));
        return geracao.andamento(midiaId, MES).orElseThrow().getFim().get(30, TimeUnit.SECONDS);
    }

    private String paginaDaEscala() throws Exception {
        return mvc.perform(get("/ministerios/{m}/escalas", midiaId)
                        .param("mes", MES.toString())
                        .with(user(gerente)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString()
                .replaceAll("\\s+", " ");
    }

    private Vaga vaga(Long eventoId, Long funcaoId) {
        return vagas.findByEventoIdIn(List.of(eventoId)).stream()
                .filter(vaga -> vaga.getFuncaoId().equals(funcaoId))
                .findFirst()
                .orElseThrow();
    }

    private Long pessoa(Long eventoId, Long funcaoId) {
        return vagas.findByEventoIdIn(List.of(eventoId)).stream()
                .filter(vaga -> vaga.getFuncaoId().equals(funcaoId))
                .findFirst()
                .orElseThrow()
                .getUsuarioId();
    }

    private static String nomeDoMes() {
        return Datas.nomeDoMes(MES).toLowerCase(Locale.ROOT);
    }
}
