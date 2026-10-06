package br.igreja.escala.escala.service;

import java.util.stream.Collectors;

/** O texto de um evento da escala publicada que o gerente cola no grupo do WhatsApp. Não envia nada. */
final class TextoParaWhatsapp {

    private TextoParaWhatsapp() {}

    /**
     * Título em negrito do WhatsApp, a data e quem está em cada função que o evento exige:
     *
     * <pre>
     * *Mídia — Culto de domingo*
     * 12/10 · Dom · 18h00
     *
     * Projeção: Ana Souza
     * Transmissão: a definir
     * </pre>
     */
    static String de(String ministerio, EscaladosDoEvento evento) {
        return "*" + ministerio + " — " + evento.nome() + "*\n" + evento.quando() + "\n\n"
                + evento.funcoes().stream()
                        .filter(EscaladosDoEvento.NaFuncao::exigida)
                        .map(funcao -> funcao.funcao() + ": " + String.join(", ", funcao.pessoas()))
                        .collect(Collectors.joining("\n"));
    }
}
