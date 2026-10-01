package br.igreja.escala.ministerio.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.igreja.escala.Pessoas;
import br.igreja.escala.compartilhado.web.MinisterioNaNavegacao;
import br.igreja.escala.identidade.domain.UsuarioAutenticado;
import br.igreja.escala.ministerio.domain.CorDoMinisterio;
import br.igreja.escala.ministerio.domain.Icone;
import br.igreja.escala.ministerio.domain.Ministerio;
import br.igreja.escala.ministerio.repository.MembresiaRepository;
import br.igreja.escala.ministerio.repository.MinisterioRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;

class MinisteriosGerenciadosServiceTest {

    private final MinisterioRepository ministerios = mock(MinisterioRepository.class);
    private final MembresiaRepository membresias = mock(MembresiaRepository.class);
    private final MinisteriosGerenciadosService servico = new MinisteriosGerenciadosService(ministerios, membresias);

    private final Ministerio louvor = ministerio(2L, "Louvor", Icone.MUSIC);
    private final Ministerio midia = ministerio(1L, "Mídia", Icone.MONITOR);

    @Test
    void gerenteVeOsMinisteriosEmQueEGerente() {
        when(membresias.ministeriosGerenciadosPor(10L)).thenReturn(List.of(midia));

        assertThat(servico.de(logado(Pessoas.membro(10L, "Gerente"))))
                .containsExactly(new MinisterioNaNavegacao(1L, "Mídia", "monitor"));
    }

    @Test
    void adminVeTodosOsMinisterios() {
        when(ministerios.findAllByOrderByNomeAsc()).thenReturn(List.of(louvor, midia));

        assertThat(servico.de(logado(Pessoas.admin(1L, "Admin"))))
                .extracting(MinisterioNaNavegacao::nome)
                .containsExactly("Louvor", "Mídia");
        verifyNoInteractions(membresias);
    }

    @Test
    void semUsuarioDoSistemaNaoHaMinisterios() {
        assertThat(servico.de(null)).isEmpty();
        assertThat(servico.de(new TestingAuthenticationToken("x", "y", "ROLE_ADMIN")))
                .isEmpty();
    }

    private static Authentication logado(UsuarioAutenticado usuario) {
        return UsernamePasswordAuthenticationToken.authenticated(usuario, null, usuario.getAuthorities());
    }

    private static Ministerio ministerio(Long id, String nome, Icone icone) {
        var ministerio = new Ministerio(nome, CorDoMinisterio.MINT, icone);
        ReflectionTestUtils.setField(ministerio, "id", id);
        return ministerio;
    }
}
