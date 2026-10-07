package br.igreja.escala.escala.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

/**
 * O PDF da escala do mês, para imprimir em A4: o título, a linha de quando foi gerado e uma tabela com um evento por
 * linha (data, horário e nome) e uma coluna por função, com um nome por linha, "a definir" em itálico na vaga
 * obrigatória vazia e "—" na função que o evento não exige. O cabeçalho da tabela se repete em cada página, e o rodapé
 * diz a página. Retrato até 4 funções; acima disso, paisagem.
 *
 * <p>Usa a Helvetica das 14 fontes padrão do PDF (sem arquivo de fonte), que cobre o português. Um caractere fora dela
 * vira a letra sem acento ou "?".
 */
final class PdfDaEscala {

    static final int FUNCOES_NO_RETRATO = 4;

    private static final float MARGEM = 40;
    private static final float TAMANHO = 10;
    private static final float ENTRELINHA = 13;
    private static final float RESPIRO = 5;
    private static final float RODAPE = 24;
    private static final float CINZA_DO_CABECALHO = 0.92f;
    private static final float CINZA_DA_LINHA = 0.75f;
    private static final float CINZA_DO_TEXTO = 0.35f;
    private static final String SEM_FUNCAO = "—";

    private final PDFont regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private final PDFont negrito = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    private final PDFont italico = new PDType1Font(Standard14Fonts.FontName.HELVETICA_OBLIQUE);

    private final PDDocument documento = new PDDocument();
    private final PDRectangle formato;
    private final float[] larguras;
    private final List<String> cabecalho;
    private PDPageContentStream pagina;
    private float y;

    private PdfDaEscala(List<String> funcoes) {
        this.formato = funcoes.size() > FUNCOES_NO_RETRATO
                ? new PDRectangle(PDRectangle.A4.getHeight(), PDRectangle.A4.getWidth())
                : PDRectangle.A4;
        float util = formato.getWidth() - 2 * MARGEM;
        float doEvento = Math.max(150, util * (funcoes.isEmpty() ? 1 : 0.3f));
        this.larguras = new float[funcoes.size() + 1];
        larguras[0] = funcoes.isEmpty() ? util : doEvento;
        for (int i = 1; i < larguras.length; i++) {
            larguras[i] = (util - doEvento) / funcoes.size();
        }
        this.cabecalho = new ArrayList<>();
        cabecalho.add("Evento");
        cabecalho.addAll(funcoes);
    }

    /**
     * @param titulo "Mídia — Outubro 2026"
     * @param subtitulo "Escala publicada · atualizada em 06/10/2026 às 14h32"
     * @param funcoes os nomes das colunas, na ordem das funções de cada evento
     * @param eventos na ordem em que vão para a tabela
     */
    static byte[] gerar(String titulo, String subtitulo, List<String> funcoes, List<EscaladosDoEvento> eventos) {
        var pdf = new PdfDaEscala(funcoes);
        try (pdf.documento) {
            pdf.documento.getDocumentInformation().setTitle(titulo);
            pdf.novaPagina();
            pdf.texto(pdf.negrito, 16, 0, MARGEM, pdf.y - 16, titulo);
            pdf.texto(pdf.regular, 9, CINZA_DO_TEXTO, MARGEM, pdf.y - 32, subtitulo);
            pdf.y -= 48;
            if (eventos.isEmpty()) {
                pdf.texto(pdf.regular, TAMANHO, 0, MARGEM, pdf.y - TAMANHO, "Nenhum evento neste mês.");
            } else {
                pdf.linhaDoCabecalho();
                for (EscaladosDoEvento evento : eventos) {
                    pdf.linhaDoEvento(evento);
                }
            }
            pdf.pagina.close();
            pdf.rodapes(titulo);
            var saida = new ByteArrayOutputStream();
            pdf.documento.save(saida);
            return saida.toByteArray();
        } catch (IOException erro) {
            throw new UncheckedIOException("Falha ao montar o PDF da escala", erro);
        }
    }

    private void novaPagina() throws IOException {
        if (pagina != null) {
            pagina.close();
        }
        var nova = new PDPage(formato);
        documento.addPage(nova);
        pagina = new PDPageContentStream(documento, nova);
        y = formato.getHeight() - MARGEM;
    }

    private void linhaDoCabecalho() throws IOException {
        var celulas = new ArrayList<List<Trecho>>();
        for (int i = 0; i < cabecalho.size(); i++) {
            celulas.add(quebrar(List.of(new Trecho(negrito, cabecalho.get(i))), larguras[i]));
        }
        float altura = altura(celulas);
        pagina.setNonStrokingColor(CINZA_DO_CABECALHO);
        pagina.addRect(MARGEM, y - altura, formato.getWidth() - 2 * MARGEM, altura);
        pagina.fill();
        desenhar(celulas, altura);
    }

    private void linhaDoEvento(EscaladosDoEvento evento) throws IOException {
        var celulas = new ArrayList<List<Trecho>>();
        celulas.add(quebrar(
                List.of(new Trecho(negrito, evento.quando()), new Trecho(regular, evento.nome())), larguras[0]));
        for (int i = 0; i < evento.funcoes().size() && i + 1 < larguras.length; i++) {
            var funcao = evento.funcoes().get(i);
            var trechos = funcao.exigida()
                    ? funcao.pessoas().stream()
                            .map(pessoa ->
                                    new Trecho(EscaladosDoEvento.A_DEFINIR.equals(pessoa) ? italico : regular, pessoa))
                            .toList()
                    : List.of(new Trecho(regular, SEM_FUNCAO));
            celulas.add(quebrar(trechos, larguras[i + 1]));
        }
        float altura = altura(celulas);
        if (y - altura < MARGEM + RODAPE) {
            novaPagina();
            linhaDoCabecalho();
        }
        desenhar(celulas, altura);
    }

    /** Escreve as células da linha a partir do topo atual e desce, com o filete embaixo. */
    private void desenhar(List<List<Trecho>> celulas, float altura) throws IOException {
        float x = MARGEM;
        for (int i = 0; i < celulas.size(); i++) {
            float base = y - RESPIRO - TAMANHO;
            for (Trecho trecho : celulas.get(i)) {
                texto(trecho.fonte(), TAMANHO, 0, x + RESPIRO, base, trecho.texto());
                base -= ENTRELINHA;
            }
            x += larguras[i];
        }
        y -= altura;
        pagina.setStrokingColor(CINZA_DA_LINHA);
        pagina.setLineWidth(0.5f);
        pagina.moveTo(MARGEM, y);
        pagina.lineTo(formato.getWidth() - MARGEM, y);
        pagina.stroke();
    }

    private static float altura(List<List<Trecho>> celulas) {
        int linhas = celulas.stream().mapToInt(List::size).max().orElse(1);
        return Math.max(1, linhas) * ENTRELINHA + 2 * RESPIRO - (ENTRELINHA - TAMANHO);
    }

    /** "Página 1 de 2" à direita e o título à esquerda, em cada página. */
    private void rodapes(String titulo) throws IOException {
        int total = documento.getNumberOfPages();
        for (int i = 0; i < total; i++) {
            try (var rodape = new PDPageContentStream(
                    documento, documento.getPage(i), PDPageContentStream.AppendMode.APPEND, true, true)) {
                pagina = rodape;
                String numero = "Página " + (i + 1) + " de " + total;
                float largura = largura(regular, 8, numero);
                texto(regular, 8, CINZA_DO_TEXTO, MARGEM, MARGEM - 12, titulo);
                texto(regular, 8, CINZA_DO_TEXTO, formato.getWidth() - MARGEM - largura, MARGEM - 12, numero);
            }
        }
    }

    private void texto(PDFont fonte, float tamanho, float cinza, float x, float base, String texto) throws IOException {
        pagina.beginText();
        pagina.setNonStrokingColor(cinza);
        pagina.setFont(fonte, tamanho);
        pagina.newLineAtOffset(x, base);
        pagina.showText(seguro(fonte, texto));
        pagina.endText();
    }

    /** Quebra cada trecho em linhas que cabem na coluna, por palavra (e por letra, se a palavra não cabe). */
    private List<Trecho> quebrar(List<Trecho> trechos, float coluna) throws IOException {
        float cabe = coluna - 2 * RESPIRO;
        var linhas = new ArrayList<Trecho>();
        for (Trecho trecho : trechos) {
            var linha = new StringBuilder();
            for (String palavra : seguro(trecho.fonte(), trecho.texto()).split(" ")) {
                String tentativa = linha.isEmpty() ? palavra : linha + " " + palavra;
                if (largura(trecho.fonte(), TAMANHO, tentativa) <= cabe) {
                    linha.setLength(0);
                    linha.append(tentativa);
                    continue;
                }
                if (!linha.isEmpty()) {
                    linhas.add(new Trecho(trecho.fonte(), linha.toString()));
                    linha.setLength(0);
                }
                for (char letra : palavra.toCharArray()) {
                    if (!linha.isEmpty() && largura(trecho.fonte(), TAMANHO, linha.toString() + letra) > cabe) {
                        linhas.add(new Trecho(trecho.fonte(), linha.toString()));
                        linha.setLength(0);
                    }
                    linha.append(letra);
                }
            }
            linhas.add(new Trecho(trecho.fonte(), linha.toString()));
        }
        return linhas;
    }

    private static float largura(PDFont fonte, float tamanho, String texto) throws IOException {
        return fonte.getStringWidth(seguro(fonte, texto)) / 1000 * tamanho;
    }

    /** O texto só com o que a fonte desenha: fora dela, a letra sem acento ou "?". */
    static String seguro(PDFont fonte, String texto) {
        var seguro = new StringBuilder();
        texto.codePoints().forEach(codigo -> {
            String letra = Character.isWhitespace(codigo) ? " " : Character.toString(codigo);
            if (desenha(fonte, letra)) {
                seguro.append(letra);
                return;
            }
            String semAcento = Normalizer.normalize(letra, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
            seguro.append(!semAcento.isEmpty() && desenha(fonte, semAcento) ? semAcento : "?");
        });
        return seguro.toString();
    }

    private static boolean desenha(PDFont fonte, String letra) {
        try {
            fonte.encode(letra);
            return true;
        } catch (IllegalArgumentException | IOException semGlifo) {
            return false;
        }
    }

    private record Trecho(PDFont fonte, String texto) {}
}
