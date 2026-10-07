package br.igreja.escala.escala.service;

import br.igreja.escala.compartilhado.Datas;
import br.igreja.escala.compartilhado.NaoEncontradoException;
import br.igreja.escala.ministerio.domain.Funcao;
import java.text.Normalizer;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * O PDF da escala publicada do mês, para o gerente baixar e imprimir: todos os eventos não cancelados, em ordem de data
 * e horário, com quem está em cada função como a escala está agora (desistências e ajustes incluídos). Rascunho não
 * sai em PDF.
 */
@Service
public class ImpressaoDaEscala {

    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final LeituraDoPeriodo leitura;
    private final Clock relogio;

    ImpressaoDaEscala(LeituraDoPeriodo leitura, Clock relogio) {
        this.leitura = leitura;
        this.relogio = relogio;
    }

    /** O arquivo para baixar: "escala-midia-2026-10.pdf". */
    public record ArquivoPdf(String nome, byte[] conteudo) {}

    /**
     * @throws NaoEncontradoException se o ministério não existe ou a escala do mês não está publicada
     */
    @Transactional(readOnly = true)
    public ArquivoPdf doMes(Long ministerioId, YearMonth mes) {
        var dados = leitura.ler(ministerioId, mes);
        if (dados.periodo() == null || !dados.periodo().isEscalaPublicada()) {
            throw new NaoEncontradoException("Escala publicada de " + mes + " no ministério " + ministerioId);
        }
        var agora = LocalDateTime.now(relogio);
        byte[] pdf = PdfDaEscala.gerar(
                dados.nomeDoMinisterio() + " — " + Datas.mesPorExtenso(mes),
                "Escala publicada · atualizada em " + DATA.format(agora) + " às " + Datas.horario(agora.toLocalTime()),
                dados.funcoes().stream().map(Funcao::getNome).toList(),
                EscaladosDoEvento.doMes(dados));
        return new ArquivoPdf("escala-" + semAcentos(dados.nomeDoMinisterio()) + "-" + mes + ".pdf", pdf);
    }

    /** "Mídia Jovem" → "midia-jovem". */
    static String semAcentos(String nome) {
        String simples = Normalizer.normalize(nome, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+|-+$)", "");
        return simples.isEmpty() ? "ministerio" : simples;
    }
}
