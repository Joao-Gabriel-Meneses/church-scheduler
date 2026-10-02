package br.igreja.escala;

import static br.igreja.escala.evento.ExemplosDeEvento.DUAS_HORAS;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.igreja.escala.compartilhado.Fuso;
import br.igreja.escala.disponibilidade.domain.Disponibilidade;
import br.igreja.escala.disponibilidade.domain.Resposta;
import br.igreja.escala.disponibilidade.repository.DisponibilidadeRepository;
import br.igreja.escala.escala.domain.MaxPorNivelParams;
import br.igreja.escala.escala.domain.Regra;
import br.igreja.escala.escala.domain.TipoDeRegra;
import br.igreja.escala.escala.domain.Vaga;
import br.igreja.escala.escala.repository.RegraRepository;
import br.igreja.escala.escala.repository.VagaRepository;
import br.igreja.escala.evento.domain.Evento;
import br.igreja.escala.evento.domain.ModeloEvento;
import br.igreja.escala.evento.domain.Periodo;
import br.igreja.escala.evento.repository.EventoRepository;
import br.igreja.escala.evento.repository.ModeloEventoRepository;
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
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
import java.util.Set;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Abre cada página do gerente como em produção: sem a transação do teste em volta da requisição. Com
 * {@code open-in-view=false}, uma associação lazy lida fora do serviço só falha assim; num teste
 * {@code @Transactional} a sessão do Hibernate continua aberta e esconde o erro.
 *
 * <p>Por isso os dados são gravados de verdade e apagados no {@code @AfterEach}, para nenhuma outra classe vê-los.
 */
@TesteDeIntegracao
class PaginasDoGerenteIT {

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
    ModeloEventoRepository modelos;

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

    private Long ministerioId;
    private YearMonth mes;
    private UsuarioAutenticado gerente;
    private UsuarioAutenticado membro;
    private UsuarioAutenticado admin;
    private Long membroId;
    private Long desativadaId;
    private Long funcaoId;
    private Long nivelId;
    private Long modeloId;
    private Long eventoDoModeloId;
    private Long eventoAvulsoId;

    @BeforeEach
    void gravaOsDados() {
        transacao.executeWithoutResult(status -> {
            var midia = ministerios.save(new Ministerio("Mídia Páginas", CorDoMinisterio.MINT, Icone.MONITOR));
            ministerioId = midia.getId();
            var paula = usuarios.save(Usuario.membro("Paula Páginas", "paula.paginas@teste.local", "{noop}x"));
            var ana = usuarios.save(Usuario.membro("Ana Páginas", "ana.paginas@teste.local", "{noop}x"));
            var bia = Usuario.membro("Bia Páginas", "bia.paginas@teste.local", "{noop}x");
            bia.desativar();
            desativadaId = usuarios.save(bia).getId();
            admin = new UsuarioAutenticado(
                    usuarios.save(Usuario.admin("Admin Páginas", "admin.paginas@teste.local", "{noop}x")));
            var membresia = new Membresia(paula.getId(), midia);
            membresia.tornarGerente();
            membresias.save(membresia);
            membresias.save(new Membresia(ana.getId(), midia));
            membresias.save(new Membresia(desativadaId, midia));
            var projecao = funcoes.save(new Funcao(midia, "Projeção", Icone.MONITOR, 1, 1));
            funcoes.save(new Funcao(midia, "Transmissão", Icone.VIDEO, 1, 1));
            var iniciante = niveis.save(new Nivel(midia, "Iniciante", 1));
            habilitacoes.save(new Habilitacao(ana.getId(), projecao, iniciante));
            var novoDomingo = new ModeloEvento(
                    midia.getId(), "Culto de domingo", DayOfWeek.SUNDAY, LocalTime.of(18, 0), DUAS_HORAS);
            novoDomingo.exigirFuncoes(Set.of(projecao.getId()));
            var domingo = modelos.save(novoDomingo);
            mes = YearMonth.now(Fuso.SAO_PAULO).plusMonths(2);
            var periodo = periodos.save(new Periodo(midia.getId(), mes));
            var dataDoDomingo = mes.atDay(1).with(TemporalAdjusters.firstInMonth(DayOfWeek.SUNDAY));
            var doModelo = eventos.save(Evento.doModelo(domingo, periodo, dataDoDomingo));
            eventoDoModeloId = doModelo.getId();
            disponibilidades.save(new Disponibilidade(ana.getId(), doModelo, Resposta.PODE, paula.getId()));
            eventoAvulsoId = eventos.save(Evento.avulso(periodo, "Ensaio", mes.atDay(10), LocalTime.NOON, DUAS_HORAS))
                    .getId();
            var daAna = new Vaga(eventoDoModeloId, projecao.getId(), 1);
            daAna.escalar(ana.getId());
            vagas.save(daAna);
            vagas.save(new Vaga(eventoAvulsoId, projecao.getId(), 1));
            var maximo = new Regra(midia.getId(), TipoDeRegra.MAX_POR_NIVEL_NO_EVENTO);
            maximo.alterar(new MaxPorNivelParams(iniciante.getId(), 1), true);
            regras.save(maximo);
            gerente = new UsuarioAutenticado(paula);
            membro = new UsuarioAutenticado(ana);
            membroId = ana.getId();
            funcaoId = projecao.getId();
            nivelId = iniciante.getId();
            modeloId = domingo.getId();
        });
    }

    @AfterEach
    void apagaOsDados() {
        transacao.executeWithoutResult(status -> {
            jdbc.update(
                    "delete from vaga where evento_id in (select id from evento where ministerio_id = ?)",
                    ministerioId);
            jdbc.update(
                    "delete from evento_funcao where evento_id in (select id from evento where ministerio_id = ?)",
                    ministerioId);
            jdbc.update(
                    "delete from modelo_evento_funcao where modelo_id in"
                            + " (select id from modelo_evento where ministerio_id = ?)",
                    ministerioId);
            jdbc.update("delete from regra where ministerio_id = ?", ministerioId);
            jdbc.update(
                    "delete from disponibilidade where evento_id in (select id from evento where ministerio_id = ?)",
                    ministerioId);
            jdbc.update("delete from evento where ministerio_id = ?", ministerioId);
            jdbc.update("delete from periodo where ministerio_id = ?", ministerioId);
            jdbc.update("delete from modelo_evento where ministerio_id = ?", ministerioId);
            jdbc.update(
                    "delete from habilitacao where funcao_id in (select id from funcao where ministerio_id = ?)",
                    ministerioId);
            jdbc.update("delete from funcao where ministerio_id = ?", ministerioId);
            jdbc.update("delete from nivel where ministerio_id = ?", ministerioId);
            jdbc.update("delete from auditoria where ministerio_id = ?", ministerioId);
            jdbc.update("delete from membresia where ministerio_id = ?", ministerioId);
            jdbc.update("delete from ministerio where id = ?", ministerioId);
            jdbc.update("delete from usuario where email like '%.paginas@teste.local'");
        });
    }

    @Test
    void paginasDosEventosAbrem() throws Exception {
        abre("/ministerios/{m}/eventos", ministerioId);
        abre("/ministerios/{m}/eventos/{e}", ministerioId, eventoDoModeloId);
        abre("/ministerios/{m}/eventos/{e}", ministerioId, eventoAvulsoId);
        abre("/ministerios/{m}/eventos/novo", ministerioId);
        abre("/ministerios/{m}/eventos/modelos", ministerioId);
        abre("/ministerios/{m}/eventos/modelos/{id}", ministerioId, modeloId);
    }

    @Test
    void paginasDosMembrosEFuncoesAbrem() throws Exception {
        abre("/ministerios/{m}/membros", ministerioId);
        abre("/ministerios/{m}/membros/{u}", ministerioId, membroId);
        abre("/ministerios/{m}/membros/novo", ministerioId);
        abre("/ministerios/{m}/membros/{u}/editar", ministerioId, membroId);
        abre("/ministerios/{m}/membros/{u}", ministerioId, desativadaId);
        abre("/ministerios/{m}/funcoes", ministerioId);
        abre("/ministerios/{m}/funcoes/{f}", ministerioId, funcaoId);
        abre("/ministerios/{m}/funcoes/niveis/{n}", ministerioId, nivelId);
    }

    @Test
    void paginasDaDisponibilidadeAbrem() throws Exception {
        mvc.perform(get("/ministerios/{m}/disponibilidade", ministerioId)
                        .param("mes", mes.toString())
                        .with(user(gerente)))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("Ana Páginas")))
                .andExpect(content()
                        .string(Matchers.containsString(CredenciaisDeTeste.URL_BASE + "/disponibilidade?mes=" + mes)));
        abre("/ministerios/{m}/disponibilidade", ministerioId);
        mvc.perform(get("/ministerios/{m}/disponibilidade/membros/{u}", ministerioId, membroId)
                        .param("mes", mes.toString())
                        .with(user(gerente)))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("Disponibilidade de Ana Páginas")));
    }

    @Test
    void disponibilidadeDoMembroAbreComQuemMarcouEmNomeDele() throws Exception {
        mvc.perform(get("/disponibilidade").param("mes", mes.toString()).with(user(membro)))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("Marcado por Paula Páginas")));
    }

    @Test
    void paginasDaEscalaEDasRegrasAbrem() throws Exception {
        mvc.perform(get("/ministerios/{m}/escalas", ministerioId)
                        .param("mes", mes.toString())
                        .with(user(gerente)))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("Ana Páginas")))
                .andExpect(content().string(Matchers.containsString("Vaga vazia")))
                .andExpect(content().string(Matchers.containsString("Sem vagas: gere a escala de novo")))
                .andExpect(content().string(Matchers.containsString("aberta")));
        abre("/ministerios/{m}/escalas", ministerioId);
        mvc.perform(get("/ministerios/{m}/regras", ministerioId).with(user(gerente)))
                .andExpect(status().isOk())
                .andExpect(
                        content().string(Matchers.containsString("No máximo 1 pessoa do nível Iniciante por evento.")));
        abre("/ministerios/{m}/regras/limite", ministerioId);
        abre("/ministerios/{m}/regras/maximo-por-nivel", ministerioId);
    }

    @Test
    void paginasDaContaAbrem() throws Exception {
        abre("/conta");
        abre("/conta/senha");
    }

    @Test
    void paginasDoAdminAbrem() throws Exception {
        mvc.perform(get("/admin/ministerios").with(user(admin))).andExpect(status().isOk());
        mvc.perform(get("/admin/ministerios/{id}", ministerioId).with(user(admin)))
                .andExpect(status().isOk());
        mvc.perform(get("/ministerios/{m}/membros", ministerioId).with(user(admin)))
                .andExpect(status().isOk());
        mvc.perform(get("/ministerios/{m}/membros/{u}", ministerioId, membroId).with(user(admin)))
                .andExpect(status().isOk());
        mvc.perform(get("/ministerios/{m}/membros/{u}", ministerioId, desativadaId)
                        .with(user(admin)))
                .andExpect(status().isOk());
    }

    private void abre(String caminho, Object... variaveis) throws Exception {
        mvc.perform(get(caminho, variaveis).with(user(gerente))).andExpect(status().isOk());
    }
}
