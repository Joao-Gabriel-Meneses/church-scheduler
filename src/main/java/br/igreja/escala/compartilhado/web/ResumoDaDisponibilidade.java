package br.igreja.escala.compartilhado.web;

import java.util.List;

/**
 * A disponibilidade de uma pessoa no mês que o gerente prepara, no início: uma linha por ministério em que ela serve.
 *
 * @param mes nome do mês ("Novembro")
 * @param url tela de marcação do mês ("/disponibilidade?mes=2026-11")
 */
public record ResumoDaDisponibilidade(String mes, String url, List<Ministerio> ministerios) {

    public ResumoDaDisponibilidade {
        ministerios = List.copyOf(ministerios);
    }

    public static ResumoDaDisponibilidade vazio() {
        return new ResumoDaDisponibilidade("", "/disponibilidade", List.of());
    }

    /**
     * @param contagem "4 de 5 respondidos"
     * @param travado o gerente travou a disponibilidade do mês
     */
    public record Ministerio(String nome, String contagem, boolean travado) {

        /** "4 de 5 respondidos" ou "Travada · 4 de 5 respondidos". */
        public String resumo() {
            return travado ? "Travada · " + contagem : contagem;
        }
    }
}
