package br.igreja.escala;

import br.igreja.escala.identidade.domain.Usuario;
import br.igreja.escala.identidade.domain.UsuarioAutenticado;
import org.springframework.test.util.ReflectionTestUtils;

/** Usuários logados para testes de controller, com id fixo (como se viessem do banco). */
public final class Pessoas {

    private Pessoas() {}

    public static UsuarioAutenticado membro(long id, String nome) {
        return autenticado(id, Usuario.membro(nome, email(nome), "hash"));
    }

    public static UsuarioAutenticado admin(long id, String nome) {
        return autenticado(id, Usuario.admin(nome, email(nome), "hash"));
    }

    private static UsuarioAutenticado autenticado(long id, Usuario usuario) {
        ReflectionTestUtils.setField(usuario, "id", id);
        return new UsuarioAutenticado(usuario);
    }

    private static String email(String nome) {
        return nome.toLowerCase().replace(' ', '.') + "@teste.local";
    }
}
