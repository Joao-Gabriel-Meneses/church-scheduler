package br.igreja.escala.identidade.service;

import br.igreja.escala.identidade.domain.Usuario;
import br.igreja.escala.identidade.repository.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Garante um admin na subida, para que alguém consiga entrar e cadastrar o resto. Roda antes dos outros runners. */
@Service
@Order(Ordered.HIGHEST_PRECEDENCE)
@EnableConfigurationProperties(AdminInicialProperties.class)
public class AdminInicialService implements ApplicationRunner {

    static final int TAMANHO_MINIMO_SENHA = Usuario.TAMANHO_MINIMO_SENHA;

    private static final Logger log = LoggerFactory.getLogger(AdminInicialService.class);

    private final UsuarioRepository usuarios;
    private final PasswordEncoder passwordEncoder;
    private final AdminInicialProperties properties;

    AdminInicialService(
            UsuarioRepository usuarios, PasswordEncoder passwordEncoder, AdminInicialProperties properties) {
        this.usuarios = usuarios;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        criarSeNecessario();
    }

    /**
     * Cria o admin a partir de {@code escala.admin.*} quando o banco ainda não tem nenhum.
     *
     * @return {@code true} se o admin foi criado agora
     * @throws IllegalStateException se não há admin e a configuração não permite criá-lo; sem admin ninguém
     *     consegue administrar o sistema, então a aplicação não deve subir
     */
    @Transactional
    public boolean criarSeNecessario() {
        if (usuarios.existsByAdminTrue()) {
            return false;
        }
        if (!properties.configurado()) {
            throw new IllegalStateException(
                    "Nenhum admin cadastrado. Defina ESCALA_ADMIN_EMAIL e ESCALA_ADMIN_SENHA para criar o primeiro.");
        }
        String email = Usuario.normalizarEmail(properties.email());
        if (usuarios.existsByEmail(email)) {
            throw new IllegalStateException("Nenhum admin cadastrado e já existe um usuário " + email
                    + " sem perfil de admin. Use outro ESCALA_ADMIN_EMAIL.");
        }
        String senha = properties.senha();
        if (senha == null || senha.length() < TAMANHO_MINIMO_SENHA) {
            throw new IllegalStateException(
                    "ESCALA_ADMIN_SENHA precisa ter pelo menos " + TAMANHO_MINIMO_SENHA + " caracteres");
        }
        String nome = properties.nome() == null || properties.nome().isBlank() ? "Administrador" : properties.nome();
        usuarios.save(Usuario.admin(nome, email, passwordEncoder.encode(senha)));
        log.info("Admin inicial {} criado.", email);
        return true;
    }
}
