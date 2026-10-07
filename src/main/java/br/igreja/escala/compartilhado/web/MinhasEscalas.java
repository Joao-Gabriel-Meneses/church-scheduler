package br.igreja.escala.compartilhado.web;

import java.util.List;

/**
 * As escalas de uma pessoa, só das escalas publicadas: as próximas (a mais perto primeiro) e as passadas recentes (a
 * mais recente primeiro, recolhidas no início), mais os ministérios de que ela é membro, para abrir a escala de cada um.
 */
public record MinhasEscalas(List<Escala> proximas, List<Escala> passadas, List<Ministerio> ministerios) {

    public MinhasEscalas {
        proximas = List.copyOf(proximas);
        passadas = List.copyOf(passadas);
        ministerios = List.copyOf(ministerios);
    }

    public static MinhasEscalas vazio() {
        return new MinhasEscalas(List.of(), List.of(), List.of());
    }

    /**
     * Uma escala da pessoa.
     *
     * @param quando "12/10 · Dom · 18h00"
     * @param tint a cor do ministério na etiqueta (mint, rose, lemon)
     * @param url a escala do ministério no mês ("/escalas/1?mes=2026-10")
     * @param vagaId a vaga em que ela está, para desistir
     * @param podeDesistir faltam pelo menos 24 h para o evento: ela desiste sozinha; depois, só o gerente muda
     */
    public record Escala(
            String quando,
            String funcao,
            String evento,
            String ministerio,
            String tint,
            String url,
            Long vagaId,
            boolean podeDesistir) {

        /** "Projeção · Culto de domingo". */
        public String descricao() {
            return funcao + " · " + evento;
        }
    }

    /** Um ministério da pessoa e a escala dele ("/escalas/1"). */
    public record Ministerio(String nome, String tint, String url) {}
}
