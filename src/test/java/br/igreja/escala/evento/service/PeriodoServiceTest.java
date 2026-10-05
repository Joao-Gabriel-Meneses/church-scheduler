package br.igreja.escala.evento.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.igreja.escala.compartilhado.NaoEncontradoException;
import br.igreja.escala.compartilhado.RegraVioladaException;
import br.igreja.escala.evento.ExemplosDeEvento;
import br.igreja.escala.evento.domain.Periodo;
import br.igreja.escala.evento.repository.PeriodoRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.YearMonth;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PeriodoServiceTest {

    private static final YearMonth OUTUBRO = YearMonth.of(2026, 10);

    private final PeriodoRepository periodos = mock(PeriodoRepository.class);
    private final EntityManager entityManager = mock(EntityManager.class);
    private final PeriodoService servico = new PeriodoService(periodos, entityManager);

    private final Periodo outubro = ExemplosDeEvento.periodo(1L, OUTUBRO);

    @Test
    void periodoEObtidoOuCriado() {
        when(periodos.findByMinisterioIdAndAnoAndMes(1L, 2026, 10)).thenReturn(Optional.empty());
        when(periodos.save(any())).thenAnswer(chamada -> chamada.getArgument(0));

        assertThat(servico.obterOuCriar(1L, OUTUBRO).getMes()).isEqualTo(OUTUBRO);
        assertThat(servico.doMes(1L, OUTUBRO)).isEmpty();
    }

    @Test
    void travaRelendoOPeriodoComALinhaBloqueadaAntesDeMudar() {
        when(periodos.findByMinisterioIdAndAnoAndMes(1L, 2026, 10)).thenReturn(Optional.of(outubro));

        assertThat(servico.travarDisponibilidade(1L, OUTUBRO)).isTrue();
        assertThat(outubro.isDisponibilidadeTravada()).isTrue();
        assertThat(servico.travarDisponibilidade(1L, OUTUBRO)).as("já travada").isFalse();
        verify(entityManager, times(2)).refresh(outubro, LockModeType.PESSIMISTIC_WRITE);

        assertThat(servico.destravarDisponibilidade(1L, OUTUBRO)).isTrue();
        assertThat(outubro.isDisponibilidadeTravada()).isFalse();
        assertThat(servico.destravarDisponibilidade(1L, OUTUBRO))
                .as("já aberta")
                .isFalse();
    }

    @Test
    void mesSemEventosNaoTemOQueTravar() {
        when(periodos.findByMinisterioIdAndAnoAndMes(1L, 2026, 10)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servico.travarDisponibilidade(1L, OUTUBRO))
                .isInstanceOf(RegraVioladaException.class)
                .hasMessage("Outubro 2026 ainda não tem eventos: não há disponibilidade para travar.");
        assertThatThrownBy(() -> servico.destravarDisponibilidade(1L, OUTUBRO))
                .hasMessage("Outubro 2026 ainda não tem eventos: não há disponibilidade para destravar.");
    }

    @Test
    void bloquearParaAlterarReleOPeriodoComALinhaBloqueada() {
        when(periodos.findById(400L)).thenReturn(Optional.of(outubro));

        assertThat(servico.bloquearParaAlterar(400L)).isSameAs(outubro);

        var ordem = inOrder(periodos, entityManager);
        ordem.verify(periodos).findById(400L);
        ordem.verify(entityManager).refresh(outubro, LockModeType.PESSIMISTIC_WRITE);
    }

    @Test
    void bloquearPeriodoQueNaoExisteE404() {
        when(periodos.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servico.bloquearParaAlterar(9L)).isInstanceOf(NaoEncontradoException.class);
    }

    @Test
    void publicaEReabreComALinhaBloqueada() {
        when(periodos.findById(400L)).thenReturn(Optional.of(outubro));

        assertThat(servico.publicarEscala(400L)).isTrue();
        assertThat(outubro.isEscalaPublicada()).isTrue();
        assertThat(servico.publicarEscala(400L)).as("já publicada").isFalse();
        assertThat(servico.reabrirEscala(400L)).isTrue();
        assertThat(outubro.isEscalaPublicada()).isFalse();
        assertThat(servico.reabrirEscala(400L)).as("já rascunho").isFalse();
        verify(entityManager, times(4)).refresh(outubro, LockModeType.PESSIMISTIC_WRITE);
    }
}
