package br.igreja.escala.identidade.service;

import br.igreja.escala.identidade.domain.Usuario;
import br.igreja.escala.identidade.repository.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Garante um admin na primeira subida, para que alguém consiga entrar e cadastrar o resto. */
@Service
@EnableConfigurationProperties(AdminInicialProperties.class)
public class AdminInicialService implements ApplicationRunner {

    static final int TAMANHO_MINIMO_SENHA = 8;

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

    /** @return {@code true} se o admin foi criado agora. */
    @Transactional
    public boolean criarSeNecessario() {
        if (usuarios.existsByAdminTrue()) {
            return false;
        }
        if (!properties.configurado()) {
            log.warn("Nenhum admin cadastrado e ESCALA_ADMIN_EMAIL não definido: ninguém conseguirá entrar.");
            return false;
        }
        String email = Usuario.normalizarEmail(properties.email());
        if (usuarios.existsByEmail(email)) {
            log.warn("Já existe um usuário {} sem perfil de admin; o admin inicial não foi criado.", email);
            return false;
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
