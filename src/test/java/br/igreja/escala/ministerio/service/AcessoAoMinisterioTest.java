package br.igreja.escala.ministerio.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.igreja.escala.Pessoas;
import br.igreja.escala.identidade.domain.UsuarioAutenticado;
import br.igreja.escala.ministerio.repository.MembresiaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

class AcessoAoMinisterioTest {

    private final MembresiaRepository membresias = mock(MembresiaRepository.class);
    private final AcessoAoMinisterio acesso = new AcessoAoMinisterio(membresias);

    @Test
    void gerenteGerenciaSoOsMinisteriosEmQueEGerente() {
        when(membresias.existsByUsuarioIdAndMinisterioIdAndGerenteTrue(10L, 1L)).thenReturn(true);
        var gerente = logado(Pessoas.membro(10L, "Gerente"));

        assertThat(acesso.podeGerenciar(gerente, 1L)).isTrue();
        assertThat(acesso.podeGerenciar(gerente, 2L)).isFalse();
    }

    @Test
    void adminGerenciaQualquerMinisterio() {
        assertThat(acesso.podeGerenciar(logado(Pessoas.admin(1L, "Admin")), 99L))
                .isTrue();
        verifyNoInteractions(membresias);
    }

    @Test
    void negaSemMinisterioSemLoginOuComOutroTipoDeUsuario() {
        var gerente = logado(Pessoas.membro(10L, "Gerente"));

        assertThat(acesso.podeGerenciar(gerente, null)).isFalse();
        assertThat(acesso.podeGerenciar(null, 1L)).isFalse();
        assertThat(acesso.podeGerenciar(new TestingAuthenticationToken("x", "y", "ROLE_ADMIN"), 1L))
                .isFalse();
    }

    private static Authentication logado(UsuarioAutenticado usuario) {
        return UsernamePasswordAuthenticationToken.authenticated(usuario, null, usuario.getAuthorities());
    }
}
