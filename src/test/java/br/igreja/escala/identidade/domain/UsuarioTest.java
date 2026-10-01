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

    @Test
    void membroCadastradoPeloGerenteEntraComSenhaProvisoriaETelefone() {
        var usuario = Usuario.comSenhaProvisoria("Ana", "Ana@X.com", " (11) 98888-7777 ", "hash");

        assertThat(usuario.isSenhaProvisoria()).isTrue();
        assertThat(usuario.isAdmin()).isFalse();
        assertThat(usuario.getEmail()).isEqualTo("ana@x.com");
        assertThat(usuario.getTelefone()).isEqualTo("(11) 98888-7777");
        assertThat(Usuario.comSenhaProvisoria("Ana", "ana@x.com", " ", "hash").getTelefone())
                .isNull();
    }

    @Test
    void recusaTelefoneLongoDemais() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Usuario.comSenhaProvisoria("Ana", "ana@x.com", "1".repeat(21), "hash"))
                .withMessageContaining("telefone");
    }

    @Test
    void senhaDefinidaPorOutraPessoaEProvisoriaEAEscolhidaPeloUsuarioNao() {
        var usuario = Usuario.membro("Ana", "ana@x.com", "hash");
        assertThat(usuario.isSenhaProvisoria()).isFalse();

        usuario.definirSenhaProvisoria("hash-do-gerente");
        assertThat(usuario.isSenhaProvisoria()).isTrue();
        assertThat(usuario.getSenhaHash()).isEqualTo("hash-do-gerente");

        usuario.definirSenha("hash-da-ana");
        assertThat(usuario.isSenhaProvisoria()).isFalse();
        assertThat(usuario.getSenhaHash()).isEqualTo("hash-da-ana");
        assertThatIllegalArgumentException().isThrownBy(() -> usuario.definirSenha(" "));
    }

    @Test
    void editaNomeEmailETelefoneNormalizados() {
        var usuario = Usuario.comSenhaProvisoria("Ana", "ana@x.com", "(11) 98888-7777", "hash");

        usuario.editarDados(" Ana Souza ", " Ana.Souza@X.com ", " ");

        assertThat(usuario.getNome()).isEqualTo("Ana Souza");
        assertThat(usuario.getEmail()).isEqualTo("ana.souza@x.com");
        assertThat(usuario.getTelefone()).isNull();
        assertThat(usuario.isSenhaProvisoria()).isTrue();
        assertThat(usuario.getSenhaHash()).isEqualTo("hash");
    }

    @Test
    void editarExigeNomeEEmail() {
        var usuario = Usuario.membro("Ana", "ana@x.com", "hash");

        assertThatIllegalArgumentException().isThrownBy(() -> usuario.editarDados(" ", "ana@x.com", null));
        assertThatIllegalArgumentException().isThrownBy(() -> usuario.editarDados("Ana", null, null));
        assertThatIllegalArgumentException().isThrownBy(() -> usuario.editarDados("Ana", "ana@x.com", "1".repeat(21)));
    }

    @Test
    void contaDesativadaNaoEntraAteSerReativada() {
        var usuario = Usuario.membro("Ana", "ana@x.com", "hash");

        usuario.desativar();
        assertThat(usuario.isAtivo()).isFalse();
        assertThat(new UsuarioAutenticado(usuario).isEnabled()).isFalse();

        usuario.reativar();
        assertThat(new UsuarioAutenticado(usuario).isEnabled()).isTrue();
        assertThat(usuario.getSenhaHash()).isEqualTo("hash");
    }
}
