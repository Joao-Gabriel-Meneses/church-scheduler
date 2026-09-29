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

    /** E-mail é a identidade de login: comparado sem diferenciar maiúsculas e sem espaços nas pontas. */
    public static String normalizarEmail(String email) {
        return exigirTexto(email, "email").strip().toLowerCase(Locale.ROOT);
    }

    public Set<Perfil> perfis() {
        return admin ? EnumSet.of(Perfil.MEMBRO, Perfil.ADMIN) : EnumSet.of(Perfil.MEMBRO);
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
}
