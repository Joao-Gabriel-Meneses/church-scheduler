package br.igreja.escala.ministerio.service;

import br.igreja.escala.ministerio.domain.Funcao;
import br.igreja.escala.ministerio.domain.Icone;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Dados para criar ou alterar uma função; também é o objeto do formulário. */
public record DadosDaFuncao(
        @NotBlank(message = "Informe o nome da função.") @Size(max = Funcao.TAMANHO_NOME, message = "Use no máximo {max} caracteres.") String nome,

        @NotNull(message = "Escolha o ícone.") Icone icone,

        @NotNull(message = "Informe quantas pessoas a função pede no mínimo.") @Min(value = 0, message = "O mínimo vai de {value} a " + Funcao.QTD_MAXIMA + ".") @Max(value = Funcao.QTD_MAXIMA, message = "O mínimo vai de 0 a {value}.") Integer qtdMin,

        @NotNull(message = "Informe quantas pessoas a função aceita no máximo.") @Min(value = 1, message = "O máximo vai de {value} a " + Funcao.QTD_MAXIMA + ".") @Max(value = Funcao.QTD_MAXIMA, message = "O máximo vai de 1 a {value}.") Integer qtdMax) {

    /** Formulário novo já com o caso mais comum: uma pessoa por evento. */
    public static DadosDaFuncao nova() {
        return new DadosDaFuncao(null, null, 1, 1);
    }

    public static DadosDaFuncao de(Funcao funcao) {
        return new DadosDaFuncao(funcao.getNome(), funcao.getIcone(), funcao.getQtdMin(), funcao.getQtdMax());
    }
}
