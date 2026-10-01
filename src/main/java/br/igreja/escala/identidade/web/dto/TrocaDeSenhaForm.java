package br.igreja.escala.identidade.web.dto;

import br.igreja.escala.identidade.domain.Usuario;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Formulário de /conta/senha. A senha atual só é pedida quando a senha não é provisória. */
public record TrocaDeSenhaForm(
        String senhaAtual,

        @NotBlank(message = "Escolha a nova senha.") @Size(
                min = Usuario.TAMANHO_MINIMO_SENHA,
                max = Usuario.TAMANHO_MAXIMO_SENHA,
                message = "A senha precisa ter de {min} a {max} caracteres.")
        String novaSenha,

        String confirmacao) {

    public static TrocaDeSenhaForm vazio() {
        return new TrocaDeSenhaForm(null, null, null);
    }

    public boolean confirmacaoConfere() {
        return novaSenha != null && novaSenha.equals(confirmacao);
    }
}
