package br.igreja.escala.escala.service;

import java.util.List;

/**
 * O que o gerente vê antes de publicar: as vagas obrigatórias vazias dos eventos por vir (com o motivo), as vagas
 * forçadas (com a regra e a justificativa) e as regras de prioridade e de preferência que mais pesam na escala.
 */
public record ResumoDaPublicacao(List<Item> vazias, List<Item> forcadas, List<Item> regras) {

    public boolean temVazias() {
        return !vazias.isEmpty();
    }

    /** Uma linha do resumo: o quê e onde (titulo) e o detalhe (motivo, justificativa ou o quanto pesa). */
    public record Item(String titulo, String detalhe) {}
}
