package br.igreja.escala.evento.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import br.igreja.escala.evento.ExemplosDeEvento;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import org.junit.jupiter.api.Test;

class EventoTest {

    private static final YearMonth OUTUBRO = YearMonth.of(2026, 10);
    private static final LocalDate DOMINGO = LocalDate.of(2026, 10, 4);

    private final Periodo outubro = ExemplosDeEvento.periodo(1L, OUTUBRO);
    private final ModeloEvento cultoDeDomingo = ExemplosDeEvento.cultoDeDomingo(1L);

    @Test
    void eventoDoModeloLevaNomeEHorarioPadrao() {
        var evento = Evento.doModelo(cultoDeDomingo, outubro, DOMINGO);

        assertThat(evento.getNome()).isEqualTo("Culto de domingo");
        assertThat(evento.getHorario()).isEqualTo(LocalTime.of(18, 0));
        assertThat(evento.getMinisterioId()).isEqualTo(1L);
        assertThat(evento.getPeriodo()).isSameAs(outubro);
        assertThat(evento.isAvulso()).isFalse();
        assertThat(evento.isCancelado()).isFalse();
    }

    @Test
    void eventoDoModeloSoNoDiaDaSemanaDeleNoMesDoPeriodoENoMesmoMinisterio() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Evento.doModelo(cultoDeDomingo, outubro, LocalDate.of(2026, 10, 5)));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Evento.doModelo(cultoDeDomingo, outubro, LocalDate.of(2026, 11, 1)));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Evento.doModelo(ExemplosDeEvento.cultoDeDomingo(2L), outubro, DOMINGO));
    }

    @Test
    void mudarOHorarioDoEventoNaoMudaOModelo() {
        var evento = Evento.doModelo(cultoDeDomingo, outubro, DOMINGO);

        evento.alterar("Culto de domingo especial", LocalTime.of(19, 0, 15));

        assertThat(evento.getHorario()).isEqualTo(LocalTime.of(19, 0));
        assertThat(cultoDeDomingo.getHorario()).isEqualTo(LocalTime.of(18, 0));
        assertThat(cultoDeDomingo.getNome()).isEqualTo("Culto de domingo");
    }

    @Test
    void avulsoMudaDeDataEDePeriodoMasODoModeloNao() {
        var conferencia =
                Evento.avulso(outubro, "Conferência de jovens", LocalDate.of(2026, 10, 17), LocalTime.of(15, 0));
        var novembro = ExemplosDeEvento.periodo(1L, YearMonth.of(2026, 11));

        conferencia.mudarData(LocalDate.of(2026, 11, 7), novembro);

        assertThat(conferencia.isAvulso()).isTrue();
        assertThat(conferencia.getPeriodo()).isSameAs(novembro);
        assertThatIllegalArgumentException()
                .isThrownBy(() -> conferencia.mudarData(LocalDate.of(2026, 12, 5), novembro));
        assertThatIllegalStateException()
                .isThrownBy(
                        () -> Evento.doModelo(cultoDeDomingo, outubro, DOMINGO).mudarData(DOMINGO, outubro));
    }

    @Test
    void cancelaEReativa() {
        var evento = Evento.doModelo(cultoDeDomingo, outubro, DOMINGO);

        evento.cancelar();
        assertThat(evento.isCancelado()).isTrue();
        evento.reativar();
        assertThat(evento.isCancelado()).isFalse();
    }

    @Test
    void avulsoExigeNomeDataEHorario() {
        assertThatIllegalArgumentException().isThrownBy(() -> Evento.avulso(outubro, " ", DOMINGO, LocalTime.NOON));
        assertThatIllegalArgumentException().isThrownBy(() -> Evento.avulso(outubro, "Ensaio", null, LocalTime.NOON));
        assertThatIllegalArgumentException().isThrownBy(() -> Evento.avulso(outubro, "Ensaio", DOMINGO, null));
        assertThatIllegalArgumentException().isThrownBy(() -> Evento.avulso(null, "Ensaio", DOMINGO, LocalTime.NOON));
    }
}
