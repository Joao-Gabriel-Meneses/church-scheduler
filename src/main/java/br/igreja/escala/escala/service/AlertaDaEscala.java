package br.igreja.escala.escala.service;

/**
 * Um AlertBanner da página de escalas: o quê e onde (título), por quê (corpo) e a regra, se houver.
 *
 * @param icone o ícone do banner; nulo é o triângulo (a desistência usa as setas da troca)
 * @param vagaId a vaga que o gerente preenche pela ação "Preencher" do banner, se houver
 */
public record AlertaDaEscala(String titulo, String corpo, String regra, String icone, Long vagaId) {

    /** Alerta sem ação, com o triângulo. */
    public AlertaDaEscala(String titulo, String corpo, String regra) {
        this(titulo, corpo, regra, null, null);
    }
}
