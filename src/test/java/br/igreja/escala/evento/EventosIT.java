package br.igreja.escala.evento;

import static br.igreja.escala.evento.ExemplosDeEvento.DUAS_HORAS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.igreja.escala.TesteDeIntegracao;
import br.igreja.escala.compartilhado.Datas;
import br.igreja.escala.compartilhado.Fuso;
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
import br.igreja.escala.ministerio.domain.Icone;
import br.igreja.escala.ministerio.domain.Membresia;
import br.igreja.escala.ministerio.domain.Ministerio;
import br.igreja.escala.ministerio.repository.FuncaoRepository;
import br.igreja.escala.ministerio.repository.MembresiaRepository;
import br.igreja.escala.ministerio.repository.MinisterioRepository;
import jakarta.persistence.EntityManager;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/** Modelos, períodos e eventos no Oracle: mapeamento de dia, horário e data, e as constraints. */
@TesteDeIntegracao
@Transactional
class EventosIT {

    @Autowired
    MinisterioRepository ministerios;

    @Autowired
    ModeloEventoRepository modelos;

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
    MockMvc mvc;

    @Autowired
    EntityManager entityManager;

    @Autowired
    JdbcTemplate jdbc;

    private Ministerio midia;

    @BeforeEach
    void criaOMinisterio() {
        midia = ministerios.save(new Ministerio("Mídia Eventos", CorDoMinisterio.MINT, Icone.MONITOR));
    }

    @Test
    void modeloGuardaDiaDaSemanaEHorarioDaIgreja() {
        var domingo = modelos.save(
                new ModeloEvento(midia.getId(), "Culto de domingo", DayOfWeek.SUNDAY, LocalTime.of(18, 0), DUAS_HORAS));
        entityManager.flush();
        entityManager.clear();

        var lido =
                modelos.findByIdAndMinisterioId(domingo.getId(), midia.getId()).orElseThrow();
        assertThat(lido.getDiaDaSemana()).isEqualTo(DayOfWeek.SUNDAY);
        assertThat(lido.getHorario()).isEqualTo(LocalTime.of(18, 0));
        assertThat(jdbc.queryForObject(
                        "select dia_semana || '/' || horario_minutos || '/' || duracao_minutos from modelo_evento"
                                + " where id = ?",
                        String.class,
                        domingo.getId()))
                .isEqualTo("7/1080/120");
    }

    @Test
    void umModeloPorMinisterioDiaEHorario() {
        var louvor = ministerios.save(new Ministerio("Louvor Modelos", CorDoMinisterio.ROSE, Icone.MUSIC));
        modelos.saveAndFlush(
                new ModeloEvento(midia.getId(), "Culto da noite", DayOfWeek.SUNDAY, LocalTime.of(18, 0), DUAS_HORAS));
        modelos.saveAndFlush(
                new ModeloEvento(midia.getId(), "Culto da manhã", DayOfWeek.SUNDAY, LocalTime.of(9, 30), DUAS_HORAS));
        modelos.saveAndFlush(
                new ModeloEvento(louvor.getId(), "Culto da noite", DayOfWeek.SUNDAY, LocalTime.of(18, 0), DUAS_HORAS));

        assertThatThrownBy(() -> modelos.saveAndFlush(
                        new ModeloEvento(midia.getId(), "Outro", DayOfWeek.SUNDAY, LocalTime.of(18, 0), DUAS_HORAS)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("UK_MODELO_EVENTO_HORARIO");
    }

    @Test
    void funcoesExigidasVaoDoModeloParaOEventoESaemComAFuncao() {
        var projecao = funcoes.save(new Funcao(midia, "Projeção", Icone.MONITOR, 1, 1));
        funcoes.save(new Funcao(midia, "Transmissão", Icone.VIDEO, 1, 1));
        var quinta = new ModeloEvento(
                midia.getId(), "Culto de quinta", DayOfWeek.THURSDAY, LocalTime.of(19, 30), DUAS_HORAS);
        quinta.exigirFuncoes(Set.of(projecao.getId()));
        modelos.save(quinta);
        var periodo = periodos.save(new Periodo(midia.getId(), MES));
        var evento = eventos.save(Evento.doModelo(
                quinta, periodo, MES.atDay(1).with(TemporalAdjusters.firstInMonth(DayOfWeek.THURSDAY))));
        entityManager.flush();
        entityManager.clear();

        assertThat(eventos.findById(evento.getId()).orElseThrow().getFuncoesExigidas())
                .containsExactly(projecao.getId());
        assertThat(modelos.findById(quinta.getId()).orElseThrow().getFuncoesExigidas())
                .containsExactly(projecao.getId());

        jdbc.update("delete from funcao where id = ?", projecao.getId());

        assertThat(jdbc.queryForObject(
                        "select count(*) from evento_funcao where evento_id = ?", Integer.class, evento.getId()))
                .as("a função excluída sai do evento, que volta a precisar de todas")
                .isZero();
        assertThat(jdbc.queryForObject(
                        "select count(*) from modelo_evento_funcao where modelo_id = ?", Integer.class, quinta.getId()))
                .isZero();
    }

    @Test
    void umPeriodoPorMinisterioEMes() {
        periodos.saveAndFlush(new Periodo(midia.getId(), YearMonth.of(2026, 10)));

        assertThat(periodos.findByMinisterioIdAndAnoAndMes(midia.getId(), 2026, 10))
                .get()
                .extracting(Periodo::getMes)
                .isEqualTo(YearMonth.of(2026, 10));
        assertThatThrownBy(() -> periodos.saveAndFlush(new Periodo(midia.getId(), YearMonth.of(2026, 10))))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    /** Daqui a dois meses: todas as datas são futuras, qualquer que seja o dia em que o teste roda. */
    private static final YearMonth MES = YearMonth.now(Fuso.SAO_PAULO).plusMonths(2);

    @Test
    void gerarOMesDeNovoNaoDuplicaNemRecriaOCancelado() throws Exception {
        var gerente = gerenteDaMidia();
        modelos.save(
                new ModeloEvento(midia.getId(), "Culto de domingo", DayOfWeek.SUNDAY, LocalTime.of(18, 0), DUAS_HORAS));
        modelos.save(new ModeloEvento(
                midia.getId(), "Culto de quinta", DayOfWeek.THURSDAY, LocalTime.of(19, 30), DUAS_HORAS));

        mvc.perform(gerar(gerente)).andExpect(flash().attributeExists("sucesso"));
        var doMes = eventosDoMes();
        long esperados = MES.atDay(1)
                .datesUntil(MES.plusMonths(1).atDay(1))
                .filter(data -> data.getDayOfWeek() == DayOfWeek.SUNDAY || data.getDayOfWeek() == DayOfWeek.THURSDAY)
                .count();
        assertThat(doMes).hasSize((int) esperados);

        var primeiro = doMes.get(0);
        mvc.perform(post("/ministerios/{m}/eventos/{e}/cancelar", midia.getId(), primeiro.getId())
                        .with(user(gerente))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection());
        mvc.perform(gerar(gerente))
                .andExpect(flash().attribute(
                                "sucesso",
                                "Nenhum evento novo em " + Datas.nomeDoMes(MES).toLowerCase(Locale.ROOT)
                                        + ": os dos modelos já existem"));

        assertThat(eventosDoMes()).hasSize((int) esperados);
        assertThat(eventos.findById(primeiro.getId()))
                .get()
                .extracting(Evento::isCancelado)
                .isEqualTo(true);
        mvc.perform(get("/ministerios/{m}/eventos", midia.getId())
                        .param("mes", MES.toString())
                        .with(user(gerente)))
                .andExpect(status().isOk());
    }

    @Test
    void doisAvulsosNoMesmoDiaPodemMasOModeloSoUmaVezPorData() {
        var periodo = periodos.save(new Periodo(midia.getId(), MES));
        var data = MES.atDay(10);
        eventos.save(Evento.avulso(periodo, "Ensaio", data, LocalTime.of(9, 0), DUAS_HORAS));
        eventos.saveAndFlush(Evento.avulso(periodo, "Reunião", data, LocalTime.of(20, 0), DUAS_HORAS));
        eventos.saveAndFlush(Evento.avulso(periodo, "Ensaio do coral", data, LocalTime.of(9, 0), DUAS_HORAS));
        var modelo = modelos.save(
                new ModeloEvento(midia.getId(), "Culto", data.getDayOfWeek(), LocalTime.of(18, 0), DUAS_HORAS));
        eventos.saveAndFlush(Evento.doModelo(modelo, periodo, data));

        assertThat(eventos.findByPeriodoIdOrderByDataAscHorarioAsc(periodo.getId()))
                .as("avulsos em horários diferentes e no mesmo horário, e o evento do modelo")
                .extracting(Evento::getNome)
                .containsExactlyInAnyOrder("Ensaio", "Ensaio do coral", "Culto", "Reunião");
        assertThat(eventos.existsByModeloIdAndData(modelo.getId(), data)).isTrue();
        assertThatThrownBy(() -> eventos.saveAndFlush(Evento.doModelo(modelo, periodo, data)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void eventoDeOutroMinisterioNaRotaDoProprioE404() throws Exception {
        var gerente = gerenteDaMidia();
        var louvor = ministerios.save(new Ministerio("Louvor Eventos", CorDoMinisterio.ROSE, Icone.MUSIC));
        var periodoDoLouvor = periodos.save(new Periodo(louvor.getId(), MES));
        var ensaio = eventos.save(
                Evento.avulso(periodoDoLouvor, "Ensaio do louvor", MES.atDay(5), LocalTime.NOON, DUAS_HORAS));

        mvc.perform(get("/ministerios/{m}/eventos/{e}", midia.getId(), ensaio.getId())
                        .with(user(gerente)))
                .andExpect(status().isNotFound());
        mvc.perform(post("/ministerios/{m}/eventos/{e}/cancelar", midia.getId(), ensaio.getId())
                        .with(user(gerente))
                        .with(csrf()))
                .andExpect(status().isNotFound());
        mvc.perform(post("/ministerios/{m}/eventos/{e}/cancelar", louvor.getId(), ensaio.getId())
                        .with(user(gerente))
                        .with(csrf()))
                .andExpect(status().isForbidden());

        assertThat(eventos.findById(ensaio.getId()))
                .get()
                .extracting(Evento::isCancelado)
                .isEqualTo(false);
    }

    @Test
    void avulsoCriadoPeloFormularioGuardaDataEHorario() throws Exception {
        var gerente = gerenteDaMidia();
        LocalDate data = MES.atDay(12);

        mvc.perform(post("/ministerios/{m}/eventos", midia.getId())
                        .with(user(gerente))
                        .with(csrf())
                        .param("nome", "Conferência de jovens")
                        .param("data", data.toString())
                        .param("horario", "15:30")
                        .param("duracaoMinutos", "120"))
                .andExpect(status().is3xxRedirection());
        entityManager.flush();
        entityManager.clear();

        assertThat(eventosDoMes()).singleElement().satisfies(evento -> {
            assertThat(evento.getData()).isEqualTo(data);
            assertThat(evento.getHorario()).isEqualTo(LocalTime.of(15, 30));
            assertThat(evento.getDuracao()).isEqualTo(Duration.ofMinutes(120));
            assertThat(evento.isAvulso()).isTrue();
        });
    }

    private List<Evento> eventosDoMes() {
        return periodos.findByMinisterioIdAndAnoAndMes(midia.getId(), MES.getYear(), MES.getMonthValue())
                .map(periodo -> eventos.findByPeriodoIdOrderByDataAscHorarioAsc(periodo.getId()))
                .orElse(List.of());
    }

    private MockHttpServletRequestBuilder gerar(UsuarioAutenticado gerente) {
        return post("/ministerios/{m}/eventos/gerar", midia.getId())
                .with(user(gerente))
                .with(csrf())
                .param("mes", MES.toString());
    }

    private UsuarioAutenticado gerenteDaMidia() {
        var gerente = usuarios.save(Usuario.membro("Gerente Eventos", "gerente.eventos@teste.local", "{noop}x"));
        var membresia = new Membresia(gerente.getId(), midia);
        membresia.tornarGerente();
        membresias.save(membresia);
        return new UsuarioAutenticado(gerente);
    }
}
