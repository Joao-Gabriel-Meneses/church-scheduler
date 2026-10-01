package br.igreja.escala.disponibilidade.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.igreja.escala.AcessoDeTeste;
import br.igreja.escala.compartilhado.NaoEncontradoException;
import br.igreja.escala.compartilhado.RegraVioladaException;
import br.igreja.escala.compartilhado.domain.AcaoAuditada;
import br.igreja.escala.compartilhado.domain.RegistroDeAuditoria;
import br.igreja.escala.compartilhado.service.AuditoriaService;
import br.igreja.escala.evento.service.PeriodoService;
import br.igreja.escala.ministerio.Exemplos;
import br.igreja.escala.ministerio.service.MinisterioService;
import java.time.YearMonth;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DisponibilidadeServiceTest {

    private static final long MIDIA = AcessoDeTeste.MIDIA;
    private static final YearMonth NOVEMBRO = YearMonth.of(2026, 11);

    private final PeriodoService periodos = mock(PeriodoService.class);
    private final MinisterioService ministerios = mock(MinisterioService.class);
    private final AuditoriaService auditoria = mock(AuditoriaService.class);
    private final DisponibilidadeService servico = new DisponibilidadeService(periodos, ministerios, auditoria);

    @BeforeEach
    void prepara() {
        when(ministerios.buscar(MIDIA)).thenReturn(Exemplos.midia());
    }

    @Test
    void travarRegistraNaAuditoria() {
        when(periodos.travarDisponibilidade(MIDIA, NOVEMBRO)).thenReturn(true);

        assertThat(servico.travar(MIDIA, NOVEMBRO, AcessoDeTeste.GERENTE_DA_MIDIA))
                .isTrue();

        verify(auditoria)
                .registrar(new RegistroDeAuditoria(
                        AcaoAuditada.TRAVAR_DISPONIBILIDADE,
                        AcessoDeTeste.GERENTE_DA_MIDIA.getId(),
                        MIDIA,
                        null,
                        "Disponibilidade de Novembro 2026 travada (Mídia)."));
    }

    @Test
    void destravarRegistraNaAuditoria() {
        when(periodos.destravarDisponibilidade(MIDIA, NOVEMBRO)).thenReturn(true);

        assertThat(servico.destravar(MIDIA, NOVEMBRO, AcessoDeTeste.ADMIN)).isTrue();

        verify(auditoria)
                .registrar(new RegistroDeAuditoria(
                        AcaoAuditada.DESTRAVAR_DISPONIBILIDADE,
                        AcessoDeTeste.ADMIN.getId(),
                        MIDIA,
                        null,
                        "Disponibilidade de Novembro 2026 destravada (Mídia)."));
    }

    @Test
    void travarOuDestravarDeNovoNaoRegistraOutraVez() {
        when(periodos.travarDisponibilidade(MIDIA, NOVEMBRO)).thenReturn(false);
        when(periodos.destravarDisponibilidade(MIDIA, NOVEMBRO)).thenReturn(false);

        assertThat(servico.travar(MIDIA, NOVEMBRO, AcessoDeTeste.GERENTE_DA_MIDIA))
                .isFalse();
        assertThat(servico.destravar(MIDIA, NOVEMBRO, AcessoDeTeste.GERENTE_DA_MIDIA))
                .isFalse();

        verify(auditoria, never()).registrar(any());
    }

    @Test
    void ministerioQueNaoExisteE404EMesSemEventosERecusado() {
        when(ministerios.buscar(9L)).thenThrow(new NaoEncontradoException("Ministério 9"));
        when(periodos.travarDisponibilidade(MIDIA, NOVEMBRO))
                .thenThrow(RegraVioladaException.geral("Novembro 2026 ainda não tem eventos."));

        assertThatThrownBy(() -> servico.travar(9L, NOVEMBRO, AcessoDeTeste.ADMIN))
                .isInstanceOf(NaoEncontradoException.class);
        assertThatThrownBy(() -> servico.travar(MIDIA, NOVEMBRO, AcessoDeTeste.ADMIN))
                .isInstanceOf(RegraVioladaException.class);
        verify(auditoria, never()).registrar(any());
    }
}
