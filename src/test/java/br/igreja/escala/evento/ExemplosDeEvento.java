package br.igreja.escala.evento;

import br.igreja.escala.evento.domain.ModeloEvento;
import br.igreja.escala.evento.domain.Periodo;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.YearMonth;
import org.springframework.test.util.ReflectionTestUtils;

/** Modelos e períodos com id, para testes sem banco. */
public final class ExemplosDeEvento {

    private ExemplosDeEvento() {}

    public static ModeloEvento cultoDeDomingo(Long ministerioId) {
        return comId(new ModeloEvento(ministerioId, "Culto de domingo", DayOfWeek.SUNDAY, LocalTime.of(18, 0)), 300L);
    }

    public static ModeloEvento cultoDeQuinta(Long ministerioId) {
        return comId(new ModeloEvento(ministerioId, "Culto de quinta", DayOfWeek.THURSDAY, LocalTime.of(19, 30)), 301L);
    }

    public static Periodo periodo(Long ministerioId, YearMonth mes) {
        return comId(new Periodo(ministerioId, mes), 400L);
    }

    public static <T> T comId(T entidade, Long id) {
        ReflectionTestUtils.setField(entidade, "id", id);
        return entidade;
    }
}
