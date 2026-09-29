package br.igreja.escala.identidade.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.igreja.escala.identidade.domain.Usuario;
import br.igreja.escala.identidade.repository.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.security.crypto.password.PasswordEncoder;

class AdminInicialServiceTest {

    private final UsuarioRepository usuarios = mock();
    private final PasswordEncoder passwordEncoder = mock();

    private AdminInicialService service(String nome, String email, String senha) {
        return new AdminInicialService(usuarios, passwordEncoder, new AdminInicialProperties(nome, email, senha));
    }

    @Test
    void criaOAdminQuandoNaoHaNenhum() {
        when(passwordEncoder.encode("senha-forte")).thenReturn("{bcrypt}hash");

        boolean criado = service("Ana", " Ana@X.com ", "senha-forte").criarSeNecessario();

        assertThat(criado).isTrue();
        var salvo = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarios).save(salvo.capture());
        assertThat(salvo.getValue().isAdmin()).isTrue();
        assertThat(salvo.getValue().getEmail()).isEqualTo("ana@x.com");
        assertThat(salvo.getValue().getNome()).isEqualTo("Ana");
        assertThat(salvo.getValue().getSenhaHash()).isEqualTo("{bcrypt}hash");
    }

    @Test
    void usaNomePadraoQuandoNaoInformado() {
        when(passwordEncoder.encode(any())).thenReturn("hash");

        service(null, "ana@x.com", "senha-forte").criarSeNecessario();

        var salvo = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarios).save(salvo.capture());
        assertThat(salvo.getValue().getNome()).isEqualTo("Administrador");
    }

    @Test
    void naoFazNadaQuandoJaExisteAdmin() {
        when(usuarios.existsByAdminTrue()).thenReturn(true);

        assertThat(service("Ana", "ana@x.com", "senha-forte").criarSeNecessario())
                .isFalse();
        verify(usuarios, never()).save(any());
    }

    @Test
    void falhaComMensagemClaraQuandoNaoHaAdminNemConfiguracao() {
        assertThatIllegalStateException()
                .isThrownBy(() -> service("Ana", " ", "senha-forte").criarSeNecessario())
                .withMessageContaining("ESCALA_ADMIN_EMAIL");
        assertThatIllegalStateException()
                .isThrownBy(() -> service(null, null, null).criarSeNecessario())
                .withMessageContaining("Nenhum admin cadastrado");
        verify(usuarios, never()).save(any());
    }

    @Test
    void naoPrecisaDeConfiguracaoQuandoJaExisteAdmin() {
        when(usuarios.existsByAdminTrue()).thenReturn(true);

        assertThat(service(null, null, null).criarSeNecessario()).isFalse();
    }

    @Test
    void falhaEmVezDeSobrescreverUsuarioExistenteComOMesmoEmail() {
        when(usuarios.existsByEmail("ana@x.com")).thenReturn(true);

        assertThatIllegalStateException()
                .isThrownBy(() -> service("Ana", "ana@x.com", "senha-forte").criarSeNecessario())
                .withMessageContaining("ana@x.com");
        verify(usuarios, never()).save(any());
    }

    @Test
    void recusaSenhaCurta() {
        assertThatIllegalStateException()
                .isThrownBy(() -> service("Ana", "ana@x.com", "curta").criarSeNecessario())
                .withMessageContaining("pelo menos 8");
        assertThatIllegalStateException()
                .isThrownBy(() -> service("Ana", "ana@x.com", null).criarSeNecessario());
    }

    @Test
    void rodaNaSubidaDaAplicacao() throws Exception {
        when(passwordEncoder.encode(any())).thenReturn("hash");

        service("Ana", "ana@x.com", "senha-forte").run(new DefaultApplicationArguments());

        verify(usuarios).save(any());
    }
}
