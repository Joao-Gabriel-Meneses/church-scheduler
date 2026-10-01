package br.igreja.escala.identidade.service;

import br.igreja.escala.identidade.domain.Usuario;
import java.util.Arrays;
import java.util.Locale;

/**
 * O que os outros módulos enxergam de um usuário. Nunca leva a senha.
 *
 * @param ativo falso se o admin desativou a conta: a pessoa não entra em nenhum ministério
 */
public record UsuarioResumo(
        Long id, String nome, String email, String telefone, boolean admin, boolean senhaProvisoria, boolean ativo) {

    static UsuarioResumo de(Usuario usuario) {
        return new UsuarioResumo(
                usuario.getId(),
                usuario.getNome(),
                usuario.getEmail(),
                usuario.getTelefone(),
                usuario.isAdmin(),
                usuario.isSenhaProvisoria(),
                usuario.isAtivo());
    }

    /** Iniciais do avatar (DataTable): a primeira letra do primeiro e do último nome ("Ana Souza" → "AS"). */
    public String iniciais() {
        String[] partes = nome.strip().split("\\s+");
        String primeira = partes[0].substring(0, 1);
        String ultima = partes.length > 1 ? partes[partes.length - 1].substring(0, 1) : "";
        return (primeira + ultima).toUpperCase(Locale.ROOT);
    }

    /** Primeiro nome, para mensagens curtas ("Senha da Ana redefinida"). */
    public String primeiroNome() {
        return Arrays.stream(nome.strip().split("\\s+")).findFirst().orElse(nome);
    }
}
