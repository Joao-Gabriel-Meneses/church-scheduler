package br.igreja.escala.ministerio.service;

import br.igreja.escala.ministerio.domain.CorDoMinisterio;
import br.igreja.escala.ministerio.domain.Icone;
import br.igreja.escala.ministerio.domain.Ministerio;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Dados para criar ou alterar um ministério; também é o objeto do formulário. */
public record DadosDoMinisterio(
        @NotBlank(message = "Informe o nome do ministério.") @Size(max = Ministerio.TAMANHO_NOME, message = "Use no máximo {max} caracteres.") String nome,

        @NotNull(message = "Escolha a cor da etiqueta.") CorDoMinisterio cor,
        @NotNull(message = "Escolha o ícone.") Icone icone) {

    public static DadosDoMinisterio vazio() {
        return new DadosDoMinisterio(null, null, null);
    }

    public static DadosDoMinisterio de(Ministerio ministerio) {
        return new DadosDoMinisterio(ministerio.getNome(), ministerio.getCor(), ministerio.getIcone());
    }
}
