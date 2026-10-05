package br.igreja.escala.escala.service;

import java.time.YearMonth;
import java.util.List;

/**
 * A escala de um mês para o gerente: a grade (cada vaga se abre para ajustar), os alertas e o resumo.
 *
 * @param temPeriodo falso se o mês ainda não tem eventos
 * @param gerada se já há vagas (a escala foi gerada ao menos uma vez)
 * @param status "Rascunho" ou "Publicada"
 * @param publicada a escala está publicada (os membros a veem)
 * @param ajustavel o gerente pode abrir as vagas para ajustar (gerada e sem geração rodando)
 * @param funcoes os nomes das colunas da grade
 */
public record PaginaDaEscala(
        YearMonth mes,
        boolean temPeriodo,
        boolean travada,
        boolean gerada,
        String status,
        boolean publicada,
        boolean ajustavel,
        List<String> funcoes,
        List<LinhaDaGrade> linhas,
        List<AlertaDaEscala> alertas,
        ResumoDaEscala resumo) {

    static PaginaDaEscala semPeriodo(YearMonth mes) {
        return new PaginaDaEscala(
                mes,
                false,
                false,
                false,
                null,
                false,
                false,
                List.of(),
                List.of(),
                List.of(),
                new ResumoDaEscala(0, 0, 0, 0, 0, List.of()));
    }
}
