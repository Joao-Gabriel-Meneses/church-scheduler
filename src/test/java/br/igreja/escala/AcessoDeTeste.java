package br.igreja.escala;

import static org.mockito.Mockito.when;

import br.igreja.escala.identidade.domain.UsuarioAutenticado;
import br.igreja.escala.ministerio.repository.MembresiaRepository;

/** Quem é quem nos testes de rota do gerente ({@link TesteDeRotaDoGerente}). */
public final class AcessoDeTeste {

    public static final long MIDIA = 1L;
    public static final long LOUVOR = 2L;

    public static final UsuarioAutenticado ADMIN = Pessoas.admin(1L, "Admin");

    /** Gerente da Mídia e de nenhum outro ministério. */
    public static final UsuarioAutenticado GERENTE_DA_MIDIA = Pessoas.membro(10L, "Gerente da Mídia");

    /** Membro comum, sem ministério que gerencie. */
    public static final UsuarioAutenticado MEMBRO = Pessoas.membro(20L, "Membro Comum");

    private AcessoDeTeste() {}

    public static void configurar(MembresiaRepository membresias) {
        when(membresias.existsByUsuarioIdAndMinisterioIdAndGerenteTrue(GERENTE_DA_MIDIA.getId(), MIDIA))
                .thenReturn(true);
    }
}
