package br.igreja.escala.escala;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.igreja.escala.TesteDeIntegracao;
import br.igreja.escala.compartilhado.Fuso;
import br.igreja.escala.disponibilidade.domain.Disponibilidade;
import br.igreja.escala.disponibilidade.domain.Resposta;
import br.igreja.escala.disponibilidade.repository.DisponibilidadeRepository;
import br.igreja.escala.escala.domain.Vaga;
import br.igreja.escala.escala.repository.VagaRepository;
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
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * A desistência de ponta a ponta, sem {@code @Transactional}: cada POST é uma transação de verdade (o período
 * bloqueado, a vaga relida), e o gerente vê o resultado no alerta, no texto do WhatsApp e no PDF. Os dados são gravados
 * de verdade e apagados no {@code @AfterEach}.
 *
 * <p>Na Mídia, com a escala do mês que vem publicada: no dia 10, o Culto da noite (18h00, Ana na Projeção) foi criado
 * antes do Culto da manhã (09h00, Bruno). A Ana também está numa reunião que começou há uma hora e num ensaio daqui a 3
 * horas (escalas publicadas), e no mês seguinte, em rascunho. O Lucas é gerente do Louvor.
 */
@TesteDeIntegracao
class DesistenciaIT {

    private static final YearMonth PUBLICADO = YearMonth.now(Fuso.SAO_PAULO).plusMonths(1);
    private static final YearMonth RASCUNHO = PUBLICADO.plusMonths(1);

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

    private Long midiaId;
    private Long louvorId;
    private Long brunoId;
    private UsuarioAutenticado gerente;
    private UsuarioAutenticado gerenteDoLouvor;
    private UsuarioAutenticado ana;
    private UsuarioAutenticado bruno;
    private Long vagaDaNoite;
    private Long vagaDaReuniao;
    private Long vagaDoEnsaio;
    private Long vagaDoRascunho;

    @BeforeEach
    void gravaOsDados() {
        transacao.executeWithoutResult(status -> {
            var midia = ministerios.save(new Ministerio("Mídia Desistência", CorDoMinisterio.MINT, Icone.MONITOR));
            var louvor = ministerios.save(new Ministerio("Louvor Desistência", CorDoMinisterio.ROSE, Icone.MUSIC));
            midiaId = midia.getId();
            louvorId = louvor.getId();
            var paula = usuarios.save(Usuario.membro("Paula Desistência", "paula.desistencia@teste.local", "{noop}x"));
            var lucas = usuarios.save(Usuario.membro("Lucas Desistência", "lucas.desistencia@teste.local", "{noop}x"));
            var daAna = usuarios.save(Usuario.membro("Ana Desistência", "ana.desistencia@teste.local", "{noop}x"));
            var doBruno =
                    usuarios.save(Usuario.membro("Bruno Desistência", "bruno.desistencia@teste.local", "{noop}x"));
            brunoId = doBruno.getId();
            var daPaula = new Membresia(paula.getId(), midia);
            daPaula.tornarGerente();
            membresias.save(daPaula);
            var doLucas = new Membresia(lucas.getId(), louvor);
            doLucas.tornarGerente();
            membresias.save(doLucas);
            membresias.save(new Membresia(daAna.getId(), midia));
            membresias.save(new Membresia(doBruno.getId(), midia));
            var projecao = funcoes.save(new Funcao(midia, "Projeção", Icone.MONITOR, 1, 1));
            var experiente = niveis.save(new Nivel(midia, "Experiente", 1));
            habilitacoes.save(new Habilitacao(daAna.getId(), projecao, experiente));
            habilitacoes.save(new Habilitacao(doBruno.getId(), projecao, experiente));

            Map<YearMonth, Periodo> publicados = new HashMap<>();
            var noite = evento(publicados, "Culto da noite", PUBLICADO.atDay(10).atTime(18, 0));
            var manha = evento(publicados, "Culto da manhã", PUBLICADO.atDay(10).atTime(9, 0));
            var agora = LocalDateTime.now(Fuso.SAO_PAULO).truncatedTo(ChronoUnit.MINUTES);
            var reuniao = evento(publicados, "Reunião", agora.minusHours(1));
            var ensaio = evento(publicados, "Ensaio", agora.plusHours(3));
            var rascunho = periodos.save(new Periodo(midiaId, RASCUNHO));
            var doRascunho = eventos.save(Evento.avulso(
                    rascunho, "Culto em rascunho", RASCUNHO.atDay(10), LocalTime.of(18, 0), Duration.ofHours(2)));
            disponibilidades.save(new Disponibilidade(brunoId, noite, Resposta.PODE, brunoId));

            vagaDaNoite = vaga(noite, projecao, daAna);
            vaga(manha, projecao, doBruno);
            vagaDaReuniao = vaga(reuniao, projecao, daAna);
            vagaDoEnsaio = vaga(ensaio, projecao, daAna);
            vagaDoRascunho = vaga(doRascunho, projecao, daAna);

            gerente = new UsuarioAutenticado(paula);
            gerenteDoLouvor = new UsuarioAutenticado(lucas);
            ana = new UsuarioAutenticado(daAna);
            bruno = new UsuarioAutenticado(doBruno);
        });
    }

    @AfterEach
    void apagaOsDados() {
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
            jdbc.update("delete from usuario where email like '%.desistencia@teste.local'");
        });
    }

    @Test
    void membroNaoDesisteDaVagaDeOutroNemPorPostDireto() throws Exception {
        mvc.perform(desistir(vagaDaNoite).with(user(bruno))).andExpect(status().isNotFound());

        var daNoite = vaga(vagaDaNoite);
        assertThat(daNoite.getUsuarioId()).isEqualTo(ana.getId());
        assertThat(daNoite.isDesistida()).isFalse();
        assertThat(desistencias()).isZero();
    }

    @Test
    void desistenciaDepoisDoInicioOuComMenosDe24HorasERecusada() throws Exception {
        mvc.perform(desistir(vagaDaReuniao).with(user(ana)))
                .andExpect(redirectedUrl("/"))
                .andExpect(flash().attribute("recusa", Matchers.containsString("já começou")));
        mvc.perform(desistir(vagaDoEnsaio).with(user(ana)))
                .andExpect(redirectedUrl("/"))
                .andExpect(flash().attribute("recusa", Matchers.startsWith("Faltam menos de 24 h para Ensaio")));

        assertThat(vaga(vagaDaReuniao).getUsuarioId()).isEqualTo(ana.getId());
        assertThat(vaga(vagaDoEnsaio).getUsuarioId()).isEqualTo(ana.getId());
        assertThat(desistencias()).isZero();
    }

    @Test
    void desistirDuasVezesEsvaziaAVagaUmaVezSo() throws Exception {
        mvc.perform(desistir(vagaDaNoite).with(user(ana)))
                .andExpect(redirectedUrl("/"))
                .andExpect(flash().attribute("sucesso", Matchers.startsWith("Você desistiu de Projeção")));
        var depoisDaPrimeira = vaga(vagaDaNoite);

        mvc.perform(desistir(vagaDaNoite).with(user(ana)))
                .andExpect(redirectedUrl("/"))
                .andExpect(flash().attribute("sucesso", Matchers.startsWith("Você já tinha desistido de Projeção")));

        var daNoite = vaga(vagaDaNoite);
        assertThat(daNoite.isVazia()).isTrue();
        assertThat(daNoite.isFixada()).isTrue();
        assertThat(daNoite.getDesistenteId()).isEqualTo(ana.getId());
        assertThat(daNoite.getVersao()).isEqualTo(depoisDaPrimeira.getVersao());
        assertThat(desistencias()).isEqualTo(1);
        mvc.perform(get("/").with(user(ana)))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.not(Matchers.containsString("Culto da noite"))));
    }

    @Test
    void gerenteVeAlertaTextoEPdfComADesistenciaEDepoisComOAjuste() throws Exception {
        mvc.perform(desistir(vagaDaNoite).with(user(ana))).andExpect(redirectedUrl("/"));

        String pagina = paginaDoGerente(PUBLICADO);
        assertThat(pagina)
                .contains("Ana Desistência desistiu de Projeção", "Culto da noite", "Preencher")
                .contains("href=\"/ministerios/" + midiaId + "/escalas/vagas/" + vagaDaNoite + "\"")
                .contains("Copiar para WhatsApp", "*Mídia Desistência — Culto da noite*", "Projeção: a definir")
                .contains("Projeção: Bruno Desistência", "Baixar PDF");
        assertThat(textoDoPdf()).contains("Culto da noite", "a definir");

        mvc.perform(post("/ministerios/{m}/escalas/vagas/{v}/escalar", midiaId, vagaDaNoite)
                        .param("usuarioId", String.valueOf(brunoId))
                        .param("versao", String.valueOf(vaga(vagaDaNoite).getVersao()))
                        .param("mes", PUBLICADO.toString())
                        .with(csrf())
                        .with(user(gerente)))
                .andExpect(flash().attribute("sucesso", Matchers.startsWith("Bruno Desistência está em Projeção")));

        String depois = paginaDoGerente(PUBLICADO);
        assertThat(depois).doesNotContain("desistiu de", "Projeção: a definir");
        assertThat(textoDoPdf()).doesNotContain("a definir");
        assertThat(vaga(vagaDaNoite).isDesistida()).isFalse();
    }

    @Test
    void pdfDoMesComDoisEventosNoMesmoDiaSaiNaOrdemDoHorario() throws Exception {
        String texto = textoDoPdf();

        assertThat(texto).contains("Mídia Desistência — ", "Culto da manhã", "Culto da noite");
        assertThat(texto.indexOf("Culto da manhã")).isLessThan(texto.indexOf("Culto da noite"));
    }

    @Test
    void gerenteDeOutroMinisterioEMembroNaoVeemAlertaTextoNemPdf() throws Exception {
        mvc.perform(desistir(vagaDaNoite).with(user(ana))).andExpect(redirectedUrl("/"));

        for (var quem : new UsuarioAutenticado[] {gerenteDoLouvor, ana}) {
            mvc.perform(get("/ministerios/{m}/escalas", midiaId)
                            .param("mes", PUBLICADO.toString())
                            .with(user(quem)))
                    .andExpect(status().isForbidden());
            mvc.perform(get("/ministerios/{m}/escalas/pdf", midiaId)
                            .param("mes", PUBLICADO.toString())
                            .with(user(quem)))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void rascunhoNaoAceitaDesistenciaNemTemTextoNemPdf() throws Exception {
        mvc.perform(desistir(vagaDoRascunho).with(user(ana))).andExpect(status().isNotFound());

        assertThat(vaga(vagaDoRascunho).getUsuarioId()).isEqualTo(ana.getId());
        assertThat(paginaDoGerente(RASCUNHO)).doesNotContain("Copiar para WhatsApp", "Baixar PDF");
        mvc.perform(get("/ministerios/{m}/escalas/pdf", midiaId)
                        .param("mes", RASCUNHO.toString())
                        .with(user(gerente)))
                .andExpect(status().isNotFound());
    }

    private Evento evento(Map<YearMonth, Periodo> publicados, String nome, LocalDateTime inicio) {
        var periodo = publicados.computeIfAbsent(YearMonth.from(inicio), mes -> {
            var novo = new Periodo(midiaId, mes);
            novo.publicarEscala();
            return periodos.save(novo);
        });
        return eventos.save(
                Evento.avulso(periodo, nome, inicio.toLocalDate(), inicio.toLocalTime(), Duration.ofHours(2)));
    }

    private Long vaga(Evento evento, Funcao funcao, Usuario pessoa) {
        var vaga = new Vaga(evento.getId(), funcao.getId(), 1);
        vaga.escalar(pessoa.getId());
        return vagas.save(vaga).getId();
    }

    private Vaga vaga(Long id) {
        return vagas.findById(id).orElseThrow();
    }

    private static MockHttpServletRequestBuilder desistir(Long vagaId) {
        return post("/escalas/vagas/{v}/desistir", vagaId).with(csrf());
    }

    private String paginaDoGerente(YearMonth mes) throws Exception {
        return mvc.perform(get("/ministerios/{m}/escalas", midiaId)
                        .param("mes", mes.toString())
                        .with(user(gerente)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    private String textoDoPdf() throws Exception {
        byte[] pdf = mvc.perform(get("/ministerios/{m}/escalas/pdf", midiaId)
                        .param("mes", PUBLICADO.toString())
                        .with(user(gerente)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andReturn()
                .getResponse()
                .getContentAsByteArray();
        try (var documento = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(documento);
        }
    }

    private int desistencias() {
        return jdbc.queryForObject(
                "select count(*) from auditoria where ministerio_id = ? and acao = 'DESISTIR_DA_VAGA'",
                Integer.class,
                midiaId);
    }
}
