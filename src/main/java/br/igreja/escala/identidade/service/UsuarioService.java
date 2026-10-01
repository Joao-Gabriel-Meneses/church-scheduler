package br.igreja.escala.identidade.service;

import br.igreja.escala.identidade.domain.Usuario;
import br.igreja.escala.identidade.domain.UsuarioAutenticado;
import br.igreja.escala.identidade.repository.UsuarioRepository;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Serviço público da identidade: o que os outros módulos podem pedir sobre usuários e senhas. */
@Service
public class UsuarioService {

    private final UsuarioRepository usuarios;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher eventos;

    UsuarioService(UsuarioRepository usuarios, PasswordEncoder passwordEncoder, ApplicationEventPublisher eventos) {
        this.usuarios = usuarios;
        this.passwordEncoder = passwordEncoder;
        this.eventos = eventos;
    }

    /**
     * @throws java.util.NoSuchElementException se o usuário não existe (o chamador já sabe que ele existe, pela
     *     membresia)
     */
    @Transactional(readOnly = true)
    public UsuarioResumo buscar(Long id) {
        return UsuarioResumo.de(usuarios.findById(id).orElseThrow());
    }

    @Transactional(readOnly = true)
    public Optional<UsuarioResumo> buscarPorEmail(String email) {
        return usuarios.findByEmail(Usuario.normalizarEmail(email)).map(UsuarioResumo::de);
    }

    /**
     * Cria a conta de um membro cadastrado pelo gerente. A senha é provisória: o membro troca no primeiro acesso.
     *
     * @throws SenhaRecusadaException no campo {@code senhaProvisoria}, se a senha não tem o tamanho certo
     * @throws IllegalStateException se o e-mail já tem conta (quem chama confere antes, com {@link #buscarPorEmail})
     */
    @Transactional
    public UsuarioResumo criarComSenhaProvisoria(NovoUsuario novo) {
        exigirTamanho(novo.senhaProvisoria(), "senhaProvisoria");
        String email = Usuario.normalizarEmail(novo.email());
        if (usuarios.existsByEmail(email)) {
            throw new IllegalStateException("Já existe conta com o e-mail " + email);
        }
        var usuario = Usuario.comSenhaProvisoria(
                novo.nome(), email, novo.telefone(), passwordEncoder.encode(novo.senhaProvisoria()));
        return UsuarioResumo.de(usuarios.save(usuario));
    }

    /**
     * Senha provisória definida por um gerente ou admin; o usuário volta a ter de trocá-la no próximo acesso, e as
     * sessões abertas dele são encerradas ({@link AcessoRevogado}). Quem pode redefinir a senha de quem é regra de quem
     * chama (o módulo ministerio).
     *
     * @throws SenhaRecusadaException no campo {@code senha}, se a senha não tem o tamanho certo
     */
    @Transactional
    public void redefinirSenhaProvisoria(Long usuarioId, String senha) {
        exigirTamanho(senha, "senha");
        usuarios.findById(usuarioId).orElseThrow().definirSenhaProvisoria(passwordEncoder.encode(senha));
        eventos.publishEvent(new AcessoRevogado(usuarioId));
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
