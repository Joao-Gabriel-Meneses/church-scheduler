package br.igreja.escala.ministerio.service;

import br.igreja.escala.ministerio.domain.Nivel;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Dados para criar ou alterar um nível; também é o objeto do formulário. */
public record DadosDoNivel(
        @NotBlank(message = "Informe o nome do nível.") @Size(max = Nivel.TAMANHO_NOME, message = "Use no máximo {max} caracteres.") String nome,

        @NotNull(message = "Informe a ordem do nível.") @Min(value = 1, message = "A ordem começa em {value}.") @Max(value = Nivel.ORDEM_MAXIMA, message = "A ordem vai até {value}.") Integer ordem) {

    public static DadosDoNivel novo(int proximaOrdem) {
        return new DadosDoNivel(null, proximaOrdem);
    }

    public static DadosDoNivel de(Nivel nivel) {
        return new DadosDoNivel(nivel.getNome(), nivel.getOrdem());
    }
}
