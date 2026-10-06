package br.igreja.escala.escala.service;

import java.time.YearMonth;
import java.util.List;

/**
 * A escala de um ministério no mês, para o membro: a grade só de leitura quando está publicada; em rascunho, nada dela
 * (nem nomes).
 *
 * @param tint a cor do ministério na etiqueta
 * @param funcoes os nomes das colunas da grade (vazia se não publicada)
 * @param linhas as linhas da grade (vazia se não publicada)
 */
public record EscalaDoMinisterio(
        Long ministerioId,
        String nome,
        String tint,
        YearMonth mes,
        boolean publicada,
        List<String> funcoes,
        List<LinhaDaGrade> linhas) {

    static EscalaDoMinisterio naoPublicada(Long ministerioId, String nome, String tint, YearMonth mes) {
        return new EscalaDoMinisterio(ministerioId, nome, tint, mes, false, List.of(), List.of());
    }
}
