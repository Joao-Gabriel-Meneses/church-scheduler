package br.igreja.escala.escala.web;

/** A geração em andamento na página: quanto tempo passou, o limite e quantas vagas a melhor escala já preencheu. */
public record GeracaoNaTela(long segundos, long limite, int preenchidas, int vagas) {

    /** "12 s de até 30 s · 20 de 28 vagas preenchidas até agora". */
    public String descricao() {
        String tempo = segundos + " s de até " + limite + " s";
        return vagas == 0 ? tempo : tempo + " · " + preenchidas + " de " + vagas + " vagas preenchidas até agora";
    }
}
