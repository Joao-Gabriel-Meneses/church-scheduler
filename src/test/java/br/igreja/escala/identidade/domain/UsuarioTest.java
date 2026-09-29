package br.igreja.escala.identidade.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

class UsuarioTest {

    @Test
    void normalizaEmailEmMinusculasESemEspacos() {
        var usuario = Usuario.membro("Ana", "  Ana.Souza@Gmail.COM ", "hash");

        assertThat(usuario.getEmail()).isEqualTo("ana.souza@gmail.com");
    }

    @Test
    void removeEspacosDoNome() {
        assertThat(Usuario.membro("  Ana Souza ", "ana@x.com", "hash").getNome())
                .isEqualTo("Ana Souza");
    }

    @Test
    void membroTemApenasPerfilDeMembro() {
        var usuario = Usuario.membro("Ana", "ana@x.com", "hash");

        assertThat(usuario.isAdmin()).isFalse();
        assertThat(usuario.perfis()).containsExactly(Perfil.MEMBRO);
    }

    @Test
    void adminTambemEMembro() {
        var usuario = Usuario.admin("Ana", "ana@x.com", "hash");

        assertThat(usuario.isAdmin()).isTrue();
        assertThat(usuario.perfis()).containsExactlyInAnyOrder(Perfil.MEMBRO, Perfil.ADMIN);
    }

    @Test
    void novoUsuarioNasceAtivo() {
        assertThat(Usuario.membro("Ana", "ana@x.com", "hash").isAtivo()).isTrue();
    }

    @Test
    void exigeNomeEmailESenha() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Usuario.membro(" ", "ana@x.com", "hash"))
                .withMessageContaining("nome");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Usuario.membro("Ana", null, "hash"))
                .withMessageContaining("email");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Usuario.membro("Ana", "ana@x.com", ""))
                .withMessageContaining("senhaHash");
    }
}
