package br.igreja.escala.identidade.service;

import br.igreja.escala.identidade.domain.Usuario;
import br.igreja.escala.identidade.domain.UsuarioAutenticado;
import br.igreja.escala.identidade.repository.UsuarioRepository;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Serviço público da identidade: o que os outros módulos podem pedir sobre usuários e senhas. */
@Service
public class UsuarioService {

    private final UsuarioRepository usuarios;
    private final PasswordEncoder passwordEncoder;

    UsuarioService(UsuarioRepository usuarios, PasswordEncoder passwordEncoder) {
        this.usuarios = usuarios;
        this.passwordEncoder = passwordEncoder;
    }

    /** Usuários pelos ids, em ordem de nome. Ids que não existem ficam de fora. */
    @Transactional(readOnly = true)
    public List<UsuarioResumo> resumos(Collection<Long> ids) {
        return usuarios.findAllById(ids).stream()
                .map(UsuarioResumo::de)
                .sorted(Comparator.comparing(UsuarioResumo::nome, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    /** Os mesmos de {@link #resumos(Collection)}, por id. */
    @Transactional(readOnly = true)
    public Map<Long, UsuarioResumo> resumosPorId(Collection<Long> ids) {
        return resumos(ids).stream().collect(Collectors.toMap(UsuarioResumo::id, Function.identity()));
    }

    /**
     * Troca a senha pelo próprio usuário. Com senha provisória não se pede a atual (acabou de ser digitada no login),
     * mas a nova precisa ser outra.
     *
     * @return o usuário da sessão atualizado, já sem a marca de senha provisória
     * @throws SenhaRecusadaException com o campo e a mensagem para o formulário
     */
    @Transactional
    public UsuarioAutenticado trocarSenha(Long usuarioId, String senhaAtual, String novaSenha) {
        var usuario = usuarios.findById(usuarioId).orElseThrow();
        if (!usuario.isSenhaProvisoria() && !confere(senhaAtual, usuario)) {
            throw new SenhaRecusadaException("senhaAtual", "A senha atual não confere.");
        }
        exigirTamanho(novaSenha, "novaSenha");
        if (confere(novaSenha, usuario)) {
            throw new SenhaRecusadaException(
                    "novaSenha",
                    usuario.isSenhaProvisoria()
                            ? "Escolha uma senha diferente da provisória."
                            : "Escolha uma senha diferente da atual.");
        }
        usuario.definirSenha(passwordEncoder.encode(novaSenha));
        return new UsuarioAutenticado(usuario);
    }

    private boolean confere(String senha, Usuario usuario) {
        return senha != null && passwordEncoder.matches(senha, usuario.getSenhaHash());
    }

    /** O formulário já valida o tamanho; aqui é a garantia para quem chama o serviço por outro caminho. */
    static void exigirTamanho(String senha, String campo) {
        if (senha == null
                || senha.length() < Usuario.TAMANHO_MINIMO_SENHA
                || senha.length() > Usuario.TAMANHO_MAXIMO_SENHA) {
            throw new SenhaRecusadaException(
                    campo,
                    "A senha precisa ter de " + Usuario.TAMANHO_MINIMO_SENHA + " a " + Usuario.TAMANHO_MAXIMO_SENHA
                            + " caracteres.");
        }
    }
}
