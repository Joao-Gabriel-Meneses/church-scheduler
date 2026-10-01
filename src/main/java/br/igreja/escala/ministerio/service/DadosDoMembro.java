package br.igreja.escala.ministerio.service;

import br.igreja.escala.identidade.domain.Usuario;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Cadastro de um membro pelo gerente; também é o objeto do formulário. Se o e-mail já tem conta, só o e-mail conta: a
 * pessoa entra no ministério sem mudar nome, telefone nem senha.
 */
public record DadosDoMembro(
        @NotBlank(message = "Informe o nome.") @Size(max = 150, message = "Use no máximo {max} caracteres.") String nome,

        @NotBlank(message = "Informe o e-mail.") @Email(message = "Informe um e-mail válido, como ana@exemplo.com.") @Size(max = 254, message = "Use no máximo {max} caracteres.") String email,

        @Pattern(
                regexp = "^$|^[0-9()+\\-\\s]{8," + Usuario.TAMANHO_TELEFONE + "}$",
                message = "Use só números, espaços, parênteses, + e -, como (11) 98888-7777.")
        String telefone,

        @NotBlank(message = "Defina a senha provisória.") @Size(
                min = Usuario.TAMANHO_MINIMO_SENHA,
                max = Usuario.TAMANHO_MAXIMO_SENHA,
                message = "A senha precisa ter de {min} a {max} caracteres.")
        String senhaProvisoria) {

    public static DadosDoMembro vazio() {
        return new DadosDoMembro(null, null, null, null);
    }
}
