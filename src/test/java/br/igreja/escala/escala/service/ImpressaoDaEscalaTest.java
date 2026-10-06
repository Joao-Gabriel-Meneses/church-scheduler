package br.igreja.escala.escala.service;

import static br.igreja.escala.evento.ExemplosDeEvento.DUAS_HORAS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import br.igreja.escala.compartilhado.Fuso;
import br.igreja.escala.compartilhado.NaoEncontradoException;
import br.igreja.escala.escala.domain.RegrasDoMinisterio;
import br.igreja.escala.escala.domain.Vaga;
import br.igreja.escala.evento.ExemplosDeEvento;
import br.igreja.escala.evento.domain.Evento;
import br.igreja.escala.evento.domain.Periodo;
import br.igreja.escala.identidade.service.UsuarioResumo;
import br.igreja.escala.ministerio.Exemplos;
import br.igreja.escala.ministerio.domain.Funcao;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

/** Outubro na Mídia: o culto da noite do dia 11 foi criado antes do da manhã. Hoje é 06/10/2026, 14h32. */
class ImpressaoDaEscalaTest {

    private static final YearMonth OUTUBRO = YearMonth.of(2026, 10);

    private final LeituraDoPeriodo leitura = mock(LeituraDoPeriodo.class);
    private final ImpressaoDaEscala impressao = new ImpressaoDaEscala(
            leitura,
            Clock.fixed(
                    ZonedDateTime.of(2026, 10, 6, 14, 32, 0, 0, Fuso.SAO_PAULO).toInstant(), Fuso.SAO_PAULO));

    private final Periodo outubro = ExemplosDeEvento.periodo(1L, OUTUBRO);
    private final Funcao projecao = Exemplos.projecao(Exemplos.midia());
    private final Evento noite = evento(501L, "Culto da noite", LocalTime.of(18, 0));
    private final Evento manha = evento(502L, "Culto da manhã", LocalTime.of(9, 30));
    private final UsuarioResumo ana = new UsuarioResumo(30L, "Ana Souza", "ana@x", null, false, false, true);
    private final UsuarioResumo bruno = new UsuarioResumo(31L, "Bruno Alves", "bruno@x", null, false, false, true);

    @Test
    void rascunhoNaoSaiEmPdf() {
        when(leitura.ler(1L, OUTUBRO)).thenReturn(dados(List.of()));

        assertThatThrownBy(() -> impressao.doMes(1L, OUTUBRO)).isInstanceOf(NaoEncontradoException.class);
    }

    @Test
    void mesSemEventosNaoSaiEmPdf() {
        when(leitura.ler(1L, OUTUBRO)).thenReturn(dados(null, List.of()));

        assertThatThrownBy(() -> impressao.doMes(1L, OUTUBRO)).isInstanceOf(NaoEncontradoException.class);
    }

    @Test
    void publicadaSaiComONomeDoArquivoEAEscalaComoEstaAgoraEmOrdemDeHorario() throws IOException {
        outubro.publicarEscala();
        var desistida = vaga(1L, noite, ana.id());
        desistida.desistir(Instant.parse("2026-10-05T17:32:00Z"));
        var ajustada = vaga(2L, manha, ana.id());
        ajustada.ajustar(bruno.id());
        when(leitura.ler(1L, OUTUBRO)).thenReturn(dados(List.of(desistida, ajustada)));

        var arquivo = impressao.doMes(1L, OUTUBRO);

        assertThat(arquivo.nome()).isEqualTo("escala-midia-2026-10.pdf");
        try (var documento = Loader.loadPDF(arquivo.conteudo())) {
            String texto = new PDFTextStripper().getText(documento);
            assertThat(texto)
                    .contains("Mídia — Outubro 2026", "Escala publicada · atualizada em 06/10/2026 às 14h32")
                    .contains("Bruno Alves", "a definir")
                    .doesNotContain("Ana Souza");
            assertThat(texto.indexOf("Culto da manhã")).isLessThan(texto.indexOf("Culto da noite"));
        }
    }

    @Test
    void nomeDoArquivoSemAcentoNemEspaco() {
        assertThat(ImpressaoDaEscala.semAcentos("Mídia Jovem")).isEqualTo("midia-jovem");
        assertThat(ImpressaoDaEscala.semAcentos("  Louvor & Artes! ")).isEqualTo("louvor-artes");
        assertThat(ImpressaoDaEscala.semAcentos("李")).isEqualTo("ministerio");
    }

    private DadosDoPeriodo dados(List<Vaga> vagas) {
        return dados(outubro, vagas);
    }

    private DadosDoPeriodo dados(Periodo periodo, List<Vaga> vagas) {
        return new DadosDoPeriodo(
                1L,
                "Mídia",
                OUTUBRO,
                periodo,
                List.of(noite, manha),
                List.of(projecao),
                vagas,
                List.of(ana, bruno),
                List.of(),
                Map.of(),
                List.of(),
                Map.of(),
                RegrasDoMinisterio.padrao(),
                List.of(),
                LocalDateTime.of(2026, 10, 6, 14, 32),
                List.of());
    }

    private Evento evento(Long id, String nome, LocalTime horario) {
        return ExemplosDeEvento.comId(
                Evento.avulso(outubro, nome, LocalDate.of(2026, 10, 11), horario, DUAS_HORAS), id);
    }

    private Vaga vaga(Long id, Evento evento, Long usuarioId) {
        var vaga = ExemplosDeEvento.comId(new Vaga(evento.getId(), projecao.getId(), 1), id);
        vaga.escalar(usuarioId);
        return vaga;
    }
}
