package br.igreja.escala.identidade.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "usuario")
public class Usuario {

    public static final int TAMANHO_MINIMO_SENHA = 8;

    /** O BCrypt só usa os primeiros 72 bytes da senha; 64 caracteres deixam folga para acentos. */
    public static final int TAMANHO_MAXIMO_SENHA = 64;

    public static final int TAMANHO_TELEFONE = 20;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String nome;

    @Column(nullable = false, unique = true, length = 254)
    private String email;

    @Column(length = 20)
    private String telefone;

    @Column(name = "senha_hash", nullable = false, length = 100)
    private String senhaHash;

    @Column(nullable = false)
    private boolean admin;

    @Column(nullable = false)
    private boolean ativo = true;

    @Column(name = "senha_provisoria", nullable = false)
    private boolean senhaProvisoria;

    @CreationTimestamp
    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    @UpdateTimestamp
    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;

    protected Usuario() {}

    private Usuario(String nome, String email, String senhaHash, boolean admin) {
        this.nome = exigirTexto(nome, "nome").strip();
        this.email = normalizarEmail(email);
        this.senhaHash = exigirTexto(senhaHash, "senhaHash");
        this.admin = admin;
    }

    public static Usuario membro(String nome, String email, String senhaHash) {
        return new Usuario(nome, email, senhaHash, false);
    }

    public static Usuario admin(String nome, String email, String senhaHash) {
        return new Usuario(nome, email, senhaHash, true);
    }

    /** Membro cadastrado pelo gerente: entra com a senha que o gerente passou e precisa trocá-la no primeiro acesso. */
    public static Usuario comSenhaProvisoria(String nome, String email, String telefone, String senhaHash) {
        var usuario = new Usuario(nome, email, senhaHash, false);
        usuario.telefone = telefoneOpcional(telefone);
        usuario.senhaProvisoria = true;
        return usuario;
    }

    /** Senha que outra pessoa definiu (gerente ou admin): o usuário vai precisar trocá-la. */
    public void definirSenhaProvisoria(String senhaHash) {
        this.senhaHash = exigirTexto(senhaHash, "senhaHash");
        this.senhaProvisoria = true;
    }

    /** Senha escolhida pelo próprio usuário. */
    public void definirSenha(String senhaHash) {
        this.senhaHash = exigirTexto(senhaHash, "senhaHash");
        this.senhaProvisoria = false;
    }

    /** E-mail é a identidade de login: comparado sem diferenciar maiúsculas e sem espaços nas pontas. */
    public static String normalizarEmail(String email) {
        return exigirTexto(email, "email").strip().toLowerCase(Locale.ROOT);
    }

    public Set<Perfil> perfis() {
        return admin ? EnumSet.of(Perfil.MEMBRO, Perfil.ADMIN) : EnumSet.of(Perfil.MEMBRO);
    }

    private static String telefoneOpcional(String telefone) {
        if (telefone == null || telefone.isBlank()) {
            return null;
        }
        String limpo = telefone.strip();
        if (limpo.length() > TAMANHO_TELEFONE) {
            throw new IllegalArgumentException("telefone tem mais de " + TAMANHO_TELEFONE + " caracteres");
        }
        return limpo;
    }

    private static String exigirTexto(String valor, String campo) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException(campo + " é obrigatório");
        }
        return valor;
    }

    public Long getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }

    public String getEmail() {
        return email;
    }

    public String getTelefone() {
        return telefone;
    }

    public String getSenhaHash() {
        return senhaHash;
    }

    public boolean isAdmin() {
        return admin;
    }

    public boolean isAtivo() {
        return ativo;
    }

    public boolean isSenhaProvisoria() {
        return senhaProvisoria;
    }
}
