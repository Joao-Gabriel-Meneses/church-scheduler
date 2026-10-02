package br.igreja.escala.evento.service;

import static br.igreja.escala.evento.ExemplosDeEvento.periodo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import br.igreja.escala.compartilhado.Fuso;
import br.igreja.escala.compartilhado.RegraVioladaException;
import br.igreja.escala.evento.repository.EventoRepository;
import br.igreja.escala.ministerio.service.FuncaoService;
import br.igreja.escala.ministerio.service.MinisterioService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Sábado, 31 de outubro de 2026, 22h em São Paulo: em UTC já é 1º de novembro. "Hoje", "este mês" e "o mês seguinte"
 * precisam ser os de São Paulo; com um relógio em UTC, o gerente perderia o último dia do mês.
 */
class ViradaDoMesTest {

    private static final Instant SABADO_22H =
            ZonedDateTime.of(2026, 10, 31, 22, 0, 0, 0, Fuso.SAO_PAULO).toInstant();

    private static final YearMonth OUTUBRO = YearMonth.of(2026, 10);
    private static final YearMonth NOVEMBRO = YearMonth.of(2026, 11);

    private final EventoRepository eventos = mock(EventoRepository.class);
    private final PeriodoService periodos = mock(PeriodoService.class);
    private final ModeloEventoService modelos = mock(ModeloEventoService.class);
    private final MinisterioService ministerios = mock(MinisterioService.class);

    private final EventoService emSaoPaulo = servico(Clock.fixed(SABADO_22H, Fuso.SAO_PAULO));
    private final EventoService emUtc = servico(Clock.fixed(SABADO_22H, ZoneOffset.UTC));

    @BeforeEach
    void prepara() {
        when(periodos.obterOuCriar(1L, OUTUBRO)).thenReturn(periodo(1L, OUTUBRO));
        when(modelos.ativos(1L)).thenReturn(List.of());
        when(eventos.save(any())).thenAnswer(chamada -> chamada.getArgument(0));
    }

    @Test
    void oMesSeguinteAindaENovembro() {
        assertThat(emSaoPaulo.proximoMes()).isEqualTo(NOVEMBRO);
        assertThat(emUtc.proximoMes()).as("em UTC já seria dezembro").isEqualTo(YearMonth.of(2026, 12));
    }

    @Test
    void outubroAindaNaoPassou() {
        assertThatNoException().isThrownBy(() -> emSaoPaulo.gerarDoMes(1L, OUTUBRO));
        assertThatThrownBy(() -> emUtc.gerarDoMes(1L, OUTUBRO))
                .as("em UTC outubro já teria passado")
                .isInstanceOf(RegraVioladaException.class);
    }

    @Test
    void hojeAindaEDia31() {
        var hojeMesmo = new DadosDoEvento("Vigília", LocalDate.of(2026, 10, 31), LocalTime.of(23, 0), 120);
        var ontem = new DadosDoEvento("Ensaio", LocalDate.of(2026, 10, 30), LocalTime.of(20, 0), 120);

        assertThat(emSaoPaulo.criarAvulso(1L, hojeMesmo).getData()).isEqualTo(LocalDate.of(2026, 10, 31));
        assertThatThrownBy(() -> emSaoPaulo.criarAvulso(1L, ontem)).isInstanceOf(RegraVioladaException.class);
        assertThatThrownBy(() -> emUtc.criarAvulso(1L, hojeMesmo))
                .as("em UTC o dia 31 já seria ontem")
                .isInstanceOf(RegraVioladaException.class);
    }

    private EventoService servico(Clock relogio) {
        return new EventoService(eventos, periodos, modelos, ministerios, mock(FuncaoService.class), relogio);
    }
}
