package br.igreja.escala.escala.service;

/** Em quantos eventos do mês a pessoa está escalada. */
public record CargaDaPessoa(String nome, String iniciais, long escalas) {

    /** "3 escalas", "1 escala", "Nenhuma escala". */
    public String descricao() {
        if (escalas == 0) {
            return "Nenhuma escala";
        }
        return escalas + (escalas == 1 ? " escala" : " escalas");
    }
}
