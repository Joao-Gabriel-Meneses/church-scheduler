package br.igreja.escala.identidade.service;

import br.igreja.escala.identidade.domain.Usuario;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Nome, e-mail e telefone de uma conta, editados pelo próprio usuário (/conta) ou por quem gerencia a conta; também é
 * o objeto do formulário. O e-mail é o login.
 */
public record DadosDaConta(
        @NotBlank(message = "Informe o nome.") @Size(max = 150, message = "Use no máximo {max} caracteres.") String nome,

        @NotBlank(message = "Informe o e-mail.") @Email(message = "Informe um e-mail válido, como ana@exemplo.com.") @Size(max = 254, message = "Use no máximo {max} caracteres.") String email,

        @Pattern(
                regexp = Usuario.FORMATO_TELEFONE,
                message = "Use só números, espaços, parênteses, + e -, como (11) 98888-7777.")
        String telefone) {

    /** Os dados atuais, para abrir o formulário preenchido. */
    public static DadosDaConta de(UsuarioResumo conta) {
        return new DadosDaConta(conta.nome(), conta.email(), conta.telefone());
    }
}
