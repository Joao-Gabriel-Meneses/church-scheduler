package br.igreja.escala.identidade.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import br.igreja.escala.identidade.domain.Usuario;
import br.igreja.escala.identidade.repository.UsuarioRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

class UsuarioDetailsServiceTest {

    private final UsuarioRepository usuarios = mock();
    private final UsuarioDetailsService service = new UsuarioDetailsService(usuarios);

    @Test
    void buscaPeloEmailNormalizado() {
        when(usuarios.findByEmail("ana@x.com")).thenReturn(Optional.of(Usuario.admin("Ana", "ana@x.com", "hash")));

        var autenticado = service.loadUserByUsername(" ANA@x.com ");

        assertThat(autenticado.getUsername()).isEqualTo("ana@x.com");
        assertThat(autenticado.getNome()).isEqualTo("Ana");
    }

    @Test
    void falhaQuandoOUsuarioNaoExiste() {
        when(usuarios.findByEmail("ninguem@x.com")).thenReturn(Optional.empty());

        assertThatExceptionOfType(UsernameNotFoundException.class)
                .isThrownBy(() -> service.loadUserByUsername("ninguem@x.com"));
    }

    @Test
    void falhaQuandoOEmailEstaVazio() {
        assertThatExceptionOfType(UsernameNotFoundException.class).isThrownBy(() -> service.loadUserByUsername(" "));
    }
}
