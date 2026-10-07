package br.igreja.escala.escala.service;

import static org.assertj.core.api.Assertions.assertThat;

import br.igreja.escala.escala.service.EscaladosDoEvento.NaFuncao;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

class PdfDaEscalaTest {

    private static final List<String> FUNCOES = List.of("Projeção", "Transmissão");

    @Test
    void a4EmRetratoComTituloCabecalhoEUmEventoPorLinha() throws IOException {
        byte[] pdf = PdfDaEscala.gerar(
                "Mídia — Outubro 2026",
                "Escala publicada · atualizada em 06/10/2026 às 14h32",
                FUNCOES,
                List.of(evento(11, 9, 30, "Culto da manhã", "Ana Souza", EscaladosDoEvento.A_DEFINIR)));

        try (var documento = Loader.loadPDF(pdf)) {
            assertThat(documento.getNumberOfPages()).isEqualTo(1);
            var formato = documento.getPage(0).getMediaBox();
            assertThat(formato.getWidth()).isEqualTo(PDRectangle.A4.getWidth());
            assertThat(formato.getHeight()).isEqualTo(PDRectangle.A4.getHeight());
            assertThat(documento.getDocumentInformation().getTitle()).isEqualTo("Mídia — Outubro 2026");
            assertThat(texto(documento))
                    .contains("Mídia — Outubro 2026", "Escala publicada · atualizada em 06/10/2026 às 14h32")
                    .contains("Evento", "Projeção", "Transmissão")
                    .contains("11/10 · Dom · 09h30", "Culto da manhã", "Ana Souza", "a definir")
                    .contains("Página 1 de 1");
        }
    }

    @Test
    void eventosDoMesmoDiaSaemNaOrdemRecebida() throws IOException {
        byte[] pdf = PdfDaEscala.gerar(
                "Mídia — Outubro 2026",
                "Escala publicada",
                FUNCOES,
                List.of(
                        evento(11, 9, 30, "Culto da manhã", "Ana Souza", "Bruno Alves"),
                        evento(11, 18, 0, "Culto da noite", "Carla Dias", "Davi Rocha")));

        try (var documento = Loader.loadPDF(pdf)) {
            String texto = texto(documento);
            assertThat(texto.indexOf("11/10 · Dom · 09h30")).isLessThan(texto.indexOf("11/10 · Dom · 18h00"));
            assertThat(texto.indexOf("Culto da manhã")).isLessThan(texto.indexOf("Culto da noite"));
        }
    }

    @Test
    void funcaoQueOEventoNaoExigeViraTraco() throws IOException {
        var casamento = new EscaladosDoEvento(
                504L,
                LocalDateTime.of(2026, 10, 24, 16, 0),
                "24/10 · Sáb · 16h00",
                "Casamento",
                List.of(
                        new NaFuncao("Projeção", true, List.of("Ana Souza")),
                        new NaFuncao("Transmissão", false, List.of())));

        try (var documento =
                Loader.loadPDF(PdfDaEscala.gerar("Mídia — Outubro 2026", "", FUNCOES, List.of(casamento)))) {
            assertThat(texto(documento)).contains("Casamento", "Ana Souza", "—");
        }
    }

    @Test
    void mesComMuitosEventosRepeteOCabecalhoEmCadaPaginaENumeraAsPaginas() throws IOException {
        var eventos = new ArrayList<EscaladosDoEvento>();
        IntStream.rangeClosed(1, 31).forEach(dia -> {
            eventos.add(evento(dia, 9, 0, "Culto da manhã", "Ana Souza", "Bruno Alves"));
            eventos.add(evento(dia, 18, 0, "Culto da noite", "Carla Dias", EscaladosDoEvento.A_DEFINIR));
        });

        try (var documento = Loader.loadPDF(PdfDaEscala.gerar("Mídia — Outubro 2026", "", FUNCOES, eventos))) {
            int paginas = documento.getNumberOfPages();
            assertThat(paginas).isGreaterThan(1);
            for (int pagina = 1; pagina <= paginas; pagina++) {
                var extrator = new PDFTextStripper();
                extrator.setStartPage(pagina);
                extrator.setEndPage(pagina);
                assertThat(extrator.getText(documento))
                        .contains("Evento", "Projeção", "Transmissão")
                        .contains("Página " + pagina + " de " + paginas);
            }
            String texto = texto(documento);
            assertThat(texto).contains("31/10 · Sáb · 18h00");
            assertThat(texto.indexOf("01/10 · Qui · 09h00")).isLessThan(texto.indexOf("31/10 · Sáb · 18h00"));
        }
    }

    @Test
    void maisDeQuatroFuncoesViraPaisagemENomesLongosQuebram() throws IOException {
        var funcoes = List.of("Projeção", "Transmissão", "Som", "Iluminação", "Fotografia");
        var evento = new EscaladosDoEvento(
                501L,
                LocalDateTime.of(2026, 10, 11, 18, 0),
                "11/10 · Dom · 18h00",
                "Culto de domingo",
                funcoes.stream()
                        .map(funcao -> new NaFuncao(funcao, true, List.of("Maria Aparecida dos Santos Albuquerque")))
                        .toList());

        try (var documento = Loader.loadPDF(PdfDaEscala.gerar("Mídia — Outubro 2026", "", funcoes, List.of(evento)))) {
            var formato = documento.getPage(0).getMediaBox();
            assertThat(formato.getWidth()).isGreaterThan(formato.getHeight());
            assertThat(texto(documento)).contains("Fotografia", "Albuquerque");
        }
    }

    @Test
    void mesSemEventosDizIsso() throws IOException {
        try (var documento = Loader.loadPDF(PdfDaEscala.gerar("Mídia — Outubro 2026", "", FUNCOES, List.of()))) {
            assertThat(texto(documento)).contains("Nenhum evento neste mês.");
        }
    }

    @Test
    void caractereForaDaFonteViraALetraSemAcentoOuInterrogacao() throws IOException {
        var fonte = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

        assertThat(PdfDaEscala.seguro(fonte, "João Conceição · Zoë — Ágape")).isEqualTo("João Conceição · Zoë — Ágape");
        assertThat(PdfDaEscala.seguro(fonte, "Łukasz Ŝ 李")).isEqualTo("?ukasz S ?");
        assertThat(PdfDaEscala.seguro(fonte, "Ana\tSouza")).isEqualTo("Ana Souza");

        byte[] pdf = PdfDaEscala.gerar(
                "Mídia — Outubro 2026", "", FUNCOES, List.of(evento(11, 18, 0, "Culto", "Łukasz Nowak", "李")));
        try (var documento = Loader.loadPDF(pdf)) {
            assertThat(texto(documento)).contains("?ukasz Nowak");
        }
    }

    private static EscaladosDoEvento evento(
            int dia, int hora, int minuto, String nome, String naProjecao, String naTransmissao) {
        var inicio = LocalDateTime.of(2026, 10, dia, hora, minuto);
        String diaDaSemana = br.igreja.escala.compartilhado.Datas.dataCurta(inicio.toLocalDate());
        return new EscaladosDoEvento(
                (long) dia * 100 + hora,
                inicio,
                diaDaSemana + " · " + br.igreja.escala.compartilhado.Datas.horario(inicio.toLocalTime()),
                nome,
                List.of(
                        new NaFuncao("Projeção", true, List.of(naProjecao)),
                        new NaFuncao("Transmissão", true, List.of(naTransmissao))));
    }

    private static String texto(PDDocument documento) throws IOException {
        return new PDFTextStripper().getText(documento);
    }
}
