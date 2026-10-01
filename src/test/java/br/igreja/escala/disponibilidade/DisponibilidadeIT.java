package br.igreja.escala.disponibilidade;

import static br.igreja.escala.evento.ExemplosDeEvento.DUAS_HORAS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.igreja.escala.TesteDeIntegracao;
import br.igreja.escala.compartilhado.Fuso;
import br.igreja.escala.disponibilidade.domain.Disponibilidade;
import br.igreja.escala.disponibilidade.domain.Resposta;
import br.igreja.escala.disponibilidade.repository.DisponibilidadeRepository;
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
import jakarta.persistence.EntityManager;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.List;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/**
 * Disponibilidade no Oracle: mapeamento, constraints e as rotas ponta a ponta, inclusive POST direto com ids de outro
 * ministério e depois da trava.
 */
@TesteDeIntegracao
@Transactional
class DisponibilidadeIT {

    /** Daqui a dois meses: todos os eventos estão por vir, qualquer que seja o dia em que o teste roda. */
    private static final YearMonth MES = YearMonth.now(Fuso.SAO_PAULO).plusMonths(2);

    @Autowired
    DisponibilidadeRepository disponibilidades;

    @Autowired
    MinisterioRepository ministerios;

    @Autowired
    PeriodoRepository periodos;

    @Autowired
    EventoRepository eventos;

    @Autowired
    UsuarioRepository usuarios;

    @Autowired
    MembresiaRepository membresias;

    @Autowired
    FuncaoRepository funcoes;

    @Autowired
    NivelRepository niveis;

    @Autowired
    HabilitacaoRepository habilitacoes;

    @Autowired
    MockMvc mvc;

    @Autowired
    EntityManager entityManager;

    @Autowired
    JdbcTemplate jdbc;

    private Ministerio midia;
    private Periodo periodo;
    private Usuario ana;
    private Evento ensaio;
    private Evento ensaioDoLouvor;

    @BeforeEach
    void criaOsDados() {
        midia = ministerios.save(new Ministerio("Mídia Disponibilidade", CorDoMinisterio.MINT, Icone.MONITOR));
        periodo = periodos.save(new Periodo(midia.getId(), MES));
        ensaio = eventos.save(Evento.avulso(periodo, "Ensaio", MES.atDay(14), LocalTime.of(15, 30), DUAS_HORAS));
        ana = usuarios.save(Usuario.membro("Ana Disponibilidade", "ana.disponibilidade@teste.local", "{noop}x"));
        servir(ana, midia);
        var louvor = ministerios.save(new Ministerio("Louvor Disponibilidade", CorDoMinisterio.ROSE, Icone.MUSIC));
        ensaioDoLouvor = eventos.save(Evento.avulso(
                periodos.save(new Periodo(louvor.getId(), MES)), "Ensaio", MES.atDay(14), LocalTime.NOON, DUAS_HORAS));
    }

    @Test
    void guardaARespostaComODiaEOHorarioDoEvento() {
        var salva = disponibilidades.save(new Disponibilidade(ana.getId(), ensaio, Resposta.NAO_PODE, ana.getId()));
        entityManager.flush();
        entityManager.clear();

        var lida = disponibilidades
                .findByUsuarioIdAndEventoId(ana.getId(), ensaio.getId())
                .orElseThrow();
        assertThat(lida.getId()).isEqualTo(salva.getId());
        assertThat(lida.getResposta()).isEqualTo(Resposta.NAO_PODE);
        assertThat(lida.getDataNaResposta()).isEqualTo(MES.atDay(14));
        assertThat(lida.getHorarioNaResposta()).isEqualTo(LocalTime.of(15, 30));
        assertThat(lida.getAtualizadoEm()).isNotNull();
        assertThat(jdbc.queryForObject(
                        "select resposta || '/' || horario_na_resposta_minutos from disponibilidade where id = ?",
                        String.class,
                        salva.getId()))
                .isEqualTo("NAO_PODE/930");
        assertThat(disponibilidades.findByUsuarioIdAndEventoIdIn(ana.getId(), List.of(ensaio.getId())))
                .hasSize(1);
        assertThat(disponibilidades.findByEventoIdIn(List.of(ensaio.getId()))).hasSize(1);
    }

    @Test
    void umaRespostaPorUsuarioEEvento() {
        disponibilidades.saveAndFlush(new Disponibilidade(ana.getId(), ensaio, Resposta.PODE, ana.getId()));

        assertThatThrownBy(() -> disponibilidades.saveAndFlush(
                        new Disponibilidade(ana.getId(), ensaio, Resposta.NAO_PODE, ana.getId())))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("UK_DISPONIBILIDADE");
    }

    @Test
    void oBancoSoAceitaAsRespostasDoModelo() {
        assertThatThrownBy(() -> jdbc.update(
                        "insert into disponibilidade (usuario_id, evento_id, resposta, data_na_resposta,"
                                + " horario_na_resposta_minutos, marcado_por_id) values (?, ?, 'TALVEZ', ?, 930, ?)",
                        ana.getId(),
                        ensaio.getId(),
                        MES.atDay(14),
                        ana.getId()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("CK_DISPONIBILIDADE_RESPOSTA");
    }

    @Test
    void membroMarcaPelaTelaETocarDuasVezesNaoDaErro() throws Exception {
        mvc.perform(get("/disponibilidade").param("mes", MES.toString()).with(user(logada(ana))))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("0 de 1 respondido")));

        for (int toque = 0; toque < 2; toque++) {
            mvc.perform(marcar(midia.getId(), ensaio.getId(), "PODE").header("HX-Request", "true"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(Matchers.containsString("1 de 1 respondido")));
        }
        mvc.perform(marcar(midia.getId(), ensaio.getId(), "NAO_PODE")).andExpect(status().is3xxRedirection());

        assertThat(disponibilidades.findByEventoIdIn(List.of(ensaio.getId())))
                .singleElement()
                .satisfies(resposta -> {
                    assertThat(resposta.getUsuarioId()).isEqualTo(ana.getId());
                    assertThat(resposta.getResposta()).isEqualTo(Resposta.NAO_PODE);
                    assertThat(resposta.getMarcadoPorId()).isEqualTo(ana.getId());
                });
    }

    @Test
    void postDiretoEmEventoDeMinisterioAlheioE404ENaoGrava() throws Exception {
        var louvor = ensaioDoLouvor.getMinisterioId();

        mvc.perform(marcar(louvor, ensaioDoLouvor.getId(), "PODE")).andExpect(status().isNotFound());
        mvc.perform(marcar(midia.getId(), ensaioDoLouvor.getId(), "PODE")).andExpect(status().isNotFound());

        assertThat(disponibilidades.findByEventoIdIn(List.of(ensaioDoLouvor.getId())))
                .isEmpty();
    }

    @Test
    void depoisDaTravaNenhumPostMudaAResposta() throws Exception {
        disponibilidades.save(new Disponibilidade(ana.getId(), ensaio, Resposta.PODE, ana.getId()));
        periodo.travarDisponibilidade();
        entityManager.flush();

        mvc.perform(marcar(midia.getId(), ensaio.getId(), "NAO_PODE").header("HX-Request", "true"))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("rt-avail rt-avail--locked")))
                .andExpect(content().string(Matchers.containsString("o gerente travou a disponibilidade")));
        mvc.perform(marcar(midia.getId(), ensaio.getId(), "NAO_PODE")).andExpect(status().is3xxRedirection());
        entityManager.clear();

        assertThat(disponibilidades.findByUsuarioIdAndEventoId(ana.getId(), ensaio.getId()))
                .get()
                .extracting(Disponibilidade::getResposta)
                .isEqualTo(Resposta.PODE);
    }

    @Test
    void prefiroNaoPorPostDiretoNaoGrava() throws Exception {
        mvc.perform(marcar(midia.getId(), ensaio.getId(), "PREFERE_NAO").header("HX-Request", "true"))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("ainda não vale")));

        assertThat(disponibilidades.findByEventoIdIn(List.of(ensaio.getId()))).isEmpty();
    }

    private MockHttpServletRequestBuilder marcar(Long ministerioId, Long eventoId, String resposta) {
        return post("/disponibilidade/ministerios/{m}/eventos/{e}", ministerioId, eventoId)
                .param("resposta", resposta)
                .param("mes", MES.toString())
                .with(user(logada(ana)))
                .with(csrf());
    }

    private static UsuarioAutenticado logada(Usuario usuario) {
        return new UsuarioAutenticado(usuario);
    }

    /** Membro do ministério com uma habilitação: quem serve e marca disponibilidade. */
    private void servir(Usuario usuario, Ministerio ministerio) {
        membresias.save(new Membresia(usuario.getId(), ministerio));
        var funcao = funcoes.save(new Funcao(ministerio, "Função " + usuario.getId(), Icone.MONITOR, 1, 1));
        var nivel = niveis.save(new Nivel(ministerio, "Nível " + usuario.getId(), 1));
        habilitacoes.save(new Habilitacao(usuario.getId(), funcao, nivel));
    }
}
