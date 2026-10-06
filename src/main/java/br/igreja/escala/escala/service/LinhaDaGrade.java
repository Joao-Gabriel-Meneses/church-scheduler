package br.igreja.escala.escala.service;

import java.util.List;

/**
 * Um evento na grade: dia ("04"), dia da semana ("Dom"), nome, horário ("18h00") e uma célula por função.
 *
 * @param comVagaVazia evento por vir com vaga obrigatória vazia (a linha ganha o filete de alerta)
 * @param whatsapp o texto do evento para o WhatsApp, só na escala publicada vista pelo gerente; nulo nas outras
 */
public record LinhaDaGrade(
        String dia,
        String diaDaSemana,
        String nome,
        String horario,
        boolean comVagaVazia,
        List<CelulaDaGrade> celulas,
        Whatsapp whatsapp) {

    /** Linha sem o texto do WhatsApp (rascunho, página do membro). */
    public LinhaDaGrade(
            String dia,
            String diaDaSemana,
            String nome,
            String horario,
            boolean comVagaVazia,
            List<CelulaDaGrade> celulas) {
        this(dia, diaDaSemana, nome, horario, comVagaVazia, celulas, null);
    }

    /**
     * "Copiar para WhatsApp" de um evento.
     *
     * @param id o id do Sheet com o texto ("whatsapp-501")
     */
    public record Whatsapp(String id, String texto) {}
}
