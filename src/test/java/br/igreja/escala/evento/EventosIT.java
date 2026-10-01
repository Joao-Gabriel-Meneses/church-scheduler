package br.igreja.escala.evento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.igreja.escala.TesteDeIntegracao;
import br.igreja.escala.evento.domain.ModeloEvento;
import br.igreja.escala.evento.domain.Periodo;
import br.igreja.escala.evento.repository.ModeloEventoRepository;
import br.igreja.escala.evento.repository.PeriodoRepository;
import br.igreja.escala.ministerio.domain.CorDoMinisterio;
import br.igreja.escala.ministerio.domain.Icone;
import br.igreja.escala.ministerio.domain.Ministerio;
import br.igreja.escala.ministerio.repository.MinisterioRepository;
import jakarta.persistence.EntityManager;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.YearMonth;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
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
                new ModeloEvento(midia.getId(), "Culto de domingo", DayOfWeek.SUNDAY, LocalTime.of(18, 0)));
        entityManager.flush();
        entityManager.clear();

        var lido =
                modelos.findByIdAndMinisterioId(domingo.getId(), midia.getId()).orElseThrow();
        assertThat(lido.getDiaDaSemana()).isEqualTo(DayOfWeek.SUNDAY);
        assertThat(lido.getHorario()).isEqualTo(LocalTime.of(18, 0));
        assertThat(jdbc.queryForObject(
                        "select dia_semana || '/' || horario_minutos from modelo_evento where id = ?",
                        String.class,
                        domingo.getId()))
                .isEqualTo("7/1080");
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
}
