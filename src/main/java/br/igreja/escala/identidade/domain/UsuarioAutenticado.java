package br.igreja.escala.identidade.domain;

import java.io.Serial;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/** Usuário logado, guardado na sessão. Os templates leem {@code principal.nome}. */
public final class UsuarioAutenticado implements UserDetails {

    @Serial
    private static final long serialVersionUID = 1L;

    private final Long id;
    private final String nome;
    private final String email;
    private final String senhaHash;
    private final boolean ativo;
    private final boolean senhaProvisoria;
    private final List<SimpleGrantedAuthority> authorities;

    public UsuarioAutenticado(Usuario usuario) {
        this.id = usuario.getId();
        this.nome = usuario.getNome();
        this.email = usuario.getEmail();
        this.senhaHash = usuario.getSenhaHash();
        this.ativo = usuario.isAtivo();
        this.senhaProvisoria = usuario.isSenhaProvisoria();
        this.authorities = usuario.perfis().stream()
                .map(perfil -> new SimpleGrantedAuthority(perfil.authority()))
                .toList();
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

    /** Enquanto for verdadeiro, só a troca de senha abre (SenhaProvisoriaInterceptor). */
    public boolean isSenhaProvisoria() {
        return senhaProvisoria;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public String getPassword() {
        return senhaHash;
    }

    @Override
    public boolean isEnabled() {
        return ativo;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }
}
