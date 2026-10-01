package br.igreja.escala.identidade.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import br.igreja.escala.identidade.domain.Usuario;
import br.igreja.escala.identidade.repository.UsuarioRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

class UsuarioServiceTest {

    /** BCrypt de verdade, com custo baixo para o teste ser rápido. */
    private final PasswordEncoder encoder = new BCryptPasswordEncoder(4);

    private final UsuarioRepository repositorio = mock(UsuarioRepository.class);
    private final UsuarioService usuarios = new UsuarioService(repositorio, encoder);

    @Test
    void comSenhaProvisoriaTrocaSemPedirAAtual() {
        var ana = cadastrada(Usuario.comSenhaProvisoria("Ana", "ana@x.com", null, encoder.encode("provisoria1")));

        var sessao = usuarios.trocarSenha(1L, null, "senha-da-ana");

        assertThat(sessao.isSenhaProvisoria()).isFalse();
        assertThat(ana.isSenhaProvisoria()).isFalse();
        assertThat(encoder.matches("senha-da-ana", ana.getSenhaHash())).isTrue();
    }

    @Test
    void novaSenhaPrecisaSerDiferenteDaProvisoria() {
        cadastrada(Usuario.comSenhaProvisoria("Ana", "ana@x.com", null, encoder.encode("provisoria1")));

        assertThatThrownBy(() -> usuarios.trocarSenha(1L, null, "provisoria1"))
                .isInstanceOfSatisfying(SenhaRecusadaException.class, recusa -> {
                    assertThat(recusa.campo()).isEqualTo("novaSenha");
                    assertThat(recusa.getMessage()).isEqualTo("Escolha uma senha diferente da provisória.");
                });
    }

    @Test
    void semSenhaProvisoriaExigeASenhaAtualCorreta() {
        var ana = cadastrada(Usuario.membro("Ana", "ana@x.com", encoder.encode("senha-antiga")));

        assertThatThrownBy(() -> usuarios.trocarSenha(1L, "errada", "senha-nova-1"))
                .isInstanceOfSatisfying(
                        SenhaRecusadaException.class,
                        recusa -> assertThat(recusa.campo()).isEqualTo("senhaAtual"));
        assertThatThrownBy(() -> usuarios.trocarSenha(1L, null, "senha-nova-1"))
                .isInstanceOf(SenhaRecusadaException.class);
        assertThatThrownBy(() -> usuarios.trocarSenha(1L, "senha-antiga", "senha-antiga"))
                .hasMessage("Escolha uma senha diferente da atual.");

        usuarios.trocarSenha(1L, "senha-antiga", "senha-nova-1");
        assertThat(encoder.matches("senha-nova-1", ana.getSenhaHash())).isTrue();
    }

    @Test
    void recusaSenhaCurtaOuLongaDemais() {
        cadastrada(Usuario.comSenhaProvisoria("Ana", "ana@x.com", null, encoder.encode("provisoria1")));

        assertThatThrownBy(() -> usuarios.trocarSenha(1L, null, "curta"))
                .hasMessage("A senha precisa ter de 8 a 64 caracteres.");
        assertThatThrownBy(() -> usuarios.trocarSenha(1L, null, "x".repeat(Usuario.TAMANHO_MAXIMO_SENHA + 1)))
                .isInstanceOf(SenhaRecusadaException.class);
        assertThatThrownBy(() -> usuarios.trocarSenha(1L, null, null)).isInstanceOf(SenhaRecusadaException.class);
    }

    @Test
    void resumosVemEmOrdemDeNomeSemASenha() {
        var bia = comId(Usuario.membro("bia Lima", "bia@x.com", "hash"), 2L);
        var ana = comId(Usuario.admin("Ana Souza", "ana@x.com", "hash"), 1L);
        when(repositorio.findAllById(List.of(1L, 2L))).thenReturn(List.of(bia, ana));

        assertThat(usuarios.resumos(List.of(1L, 2L)))
                .containsExactly(
                        new UsuarioResumo(1L, "Ana Souza", "ana@x.com", null, true, false),
                        new UsuarioResumo(2L, "bia Lima", "bia@x.com", null, false, false));
        assertThat(usuarios.resumosPorId(List.of(1L, 2L))).containsOnlyKeys(1L, 2L);
    }

    private static Usuario comId(Usuario usuario, Long id) {
        ReflectionTestUtils.setField(usuario, "id", id);
        return usuario;
    }

    private Usuario cadastrada(Usuario usuario) {
        when(repositorio.findById(1L)).thenReturn(Optional.of(usuario));
        return usuario;
    }
}
