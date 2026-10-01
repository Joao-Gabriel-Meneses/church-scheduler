package br.igreja.escala.identidade.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;

class UsuarioAutenticadoTest {

    @Test
    void usaEmailComoLoginEHashComoSenha() {
        var autenticado = new UsuarioAutenticado(Usuario.membro("Ana", "ana@x.com", "{bcrypt}hash"));

        assertThat(autenticado.getUsername()).isEqualTo("ana@x.com");
        assertThat(autenticado.getEmail()).isEqualTo("ana@x.com");
        assertThat(autenticado.getPassword()).isEqualTo("{bcrypt}hash");
        assertThat(autenticado.getNome()).isEqualTo("Ana");
        assertThat(autenticado.isEnabled()).isTrue();
    }

    @Test
    void converteOsPerfisEmRoles() {
        var membro = new UsuarioAutenticado(Usuario.membro("Ana", "ana@x.com", "hash"));
        var admin = new UsuarioAutenticado(Usuario.admin("Bia", "bia@x.com", "hash"));

        assertThat(membro.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_MEMBRO");
        assertThat(admin.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("ROLE_MEMBRO", "ROLE_ADMIN");
    }

    @Test
    void levaAMarcaDeSenhaProvisoriaParaASessao() {
        var comProvisoria = Usuario.comSenhaProvisoria("Ana", "ana@x.com", null, "hash");

        assertThat(new UsuarioAutenticado(comProvisoria).isSenhaProvisoria()).isTrue();
        assertThat(new UsuarioAutenticado(Usuario.membro("Bia", "bia@x.com", "hash")).isSenhaProvisoria())
                .isFalse();
    }
}
