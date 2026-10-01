package br.igreja.escala.disponibilidade.domain;

import static br.igreja.escala.evento.ExemplosDeEvento.DUAS_HORAS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import br.igreja.escala.evento.ExemplosDeEvento;
import br.igreja.escala.evento.domain.Evento;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import org.junit.jupiter.api.Test;

class DisponibilidadeTest {

    private static final long ANA = 20L;
    private static final long PAULA = 10L;

    private final Evento ensaio = ExemplosDeEvento.comId(
            Evento.avulso(
                    ExemplosDeEvento.periodo(1L, YearMonth.of(2026, 11)),
                    "Ensaio",
                    LocalDate.of(2026, 11, 14),
                    LocalTime.of(15, 0),
                    DUAS_HORAS),
            500L);

    @Test
    void guardaARespostaEOEventoComoEstavaNaHora() {
        var disponibilidade = new Disponibilidade(ANA, ensaio, Resposta.PODE, ANA);

        assertThat(disponibilidade.getUsuarioId()).isEqualTo(ANA);
        assertThat(disponibilidade.getEventoId()).isEqualTo(500L);
        assertThat(disponibilidade.getResposta()).isEqualTo(Resposta.PODE);
        assertThat(disponibilidade.getDataNaResposta()).isEqualTo(LocalDate.of(2026, 11, 14));
        assertThat(disponibilidade.getHorarioNaResposta()).isEqualTo(LocalTime.of(15, 0));
        assertThat(disponibilidade.marcadaPorOutro()).isFalse();
        assertThat(disponibilidade.eventoMudou(ensaio)).isFalse();
    }

    @Test
    void mesmaRespostaNaoMudaNadaNemQuemMarcou() {
        var disponibilidade = new Disponibilidade(ANA, ensaio, Resposta.PODE, ANA);

        assertThat(disponibilidade.marcar(Resposta.PODE, ensaio, ANA)).isFalse();
        assertThat(disponibilidade.marcar(Resposta.PODE, ensaio, PAULA)).isFalse();
        assertThat(disponibilidade.getMarcadoPorId()).isEqualTo(ANA);
    }

    @Test
    void outraRespostaMudaEGuardaQuemMarcou() {
        var disponibilidade = new Disponibilidade(ANA, ensaio, Resposta.PODE, ANA);

        assertThat(disponibilidade.marcar(Resposta.NAO_PODE, ensaio, PAULA)).isTrue();

        assertThat(disponibilidade.getResposta()).isEqualTo(Resposta.NAO_PODE);
        assertThat(disponibilidade.marcadaPorOutro()).isTrue();
    }

    @Test
    void horarioOuDataQueMudaramAvisamEAMesmaRespostaConfirma() {
        var disponibilidade = new Disponibilidade(ANA, ensaio, Resposta.PODE, ANA);

        ensaio.alterar("Ensaio", LocalTime.of(16, 0), DUAS_HORAS);
        assertThat(disponibilidade.eventoMudou(ensaio)).isTrue();
        assertThat(disponibilidade.getResposta()).isEqualTo(Resposta.PODE);

        assertThat(disponibilidade.marcar(Resposta.PODE, ensaio, ANA)).isTrue();
        assertThat(disponibilidade.eventoMudou(ensaio)).isFalse();
        assertThat(disponibilidade.getHorarioNaResposta()).isEqualTo(LocalTime.of(16, 0));

        ensaio.mudarData(LocalDate.of(2026, 11, 15), ensaio.getPeriodo());
        assertThat(disponibilidade.eventoMudou(ensaio)).isTrue();
    }

    @Test
    void exigeUsuarioEventoComIdRespostaEQuemMarcou() {
        var semId =
                Evento.avulso(ensaio.getPeriodo(), "Ensaio", LocalDate.of(2026, 11, 14), LocalTime.NOON, DUAS_HORAS);

        assertThatIllegalArgumentException().isThrownBy(() -> new Disponibilidade(null, ensaio, Resposta.PODE, ANA));
        assertThatIllegalArgumentException().isThrownBy(() -> new Disponibilidade(ANA, null, Resposta.PODE, ANA));
        assertThatIllegalArgumentException().isThrownBy(() -> new Disponibilidade(ANA, semId, Resposta.PODE, ANA));
        assertThatIllegalArgumentException().isThrownBy(() -> new Disponibilidade(ANA, ensaio, null, ANA));
        assertThatIllegalArgumentException().isThrownBy(() -> new Disponibilidade(ANA, ensaio, Resposta.PODE, null));
    }

    @Test
    void naoMarcaOutroEvento() {
        var disponibilidade = new Disponibilidade(ANA, ensaio, Resposta.PODE, ANA);
        var outro = ExemplosDeEvento.comId(
                Evento.avulso(
                        ensaio.getPeriodo(), "Culto", LocalDate.of(2026, 11, 15), LocalTime.of(18, 0), DUAS_HORAS),
                501L);

        assertThatIllegalArgumentException().isThrownBy(() -> disponibilidade.marcar(Resposta.NAO_PODE, outro, ANA));
    }

    @Test
    void respostasTemRotuloDoDesign() {
        assertThat(Resposta.PODE.rotulo()).isEqualTo("Pode");
        assertThat(Resposta.NAO_PODE.rotulo()).isEqualTo("Não pode");
        assertThat(Resposta.PREFERE_NAO.rotulo()).isEqualTo("Prefiro não, mas posso");
    }
}
