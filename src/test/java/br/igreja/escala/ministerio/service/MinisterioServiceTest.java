package br.igreja.escala.ministerio.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.igreja.escala.compartilhado.NaoEncontradoException;
import br.igreja.escala.compartilhado.RegraVioladaException;
import br.igreja.escala.identidade.service.UsuarioResumo;
import br.igreja.escala.identidade.service.UsuarioService;
import br.igreja.escala.ministerio.domain.CorDoMinisterio;
import br.igreja.escala.ministerio.domain.Icone;
import br.igreja.escala.ministerio.domain.Membresia;
import br.igreja.escala.ministerio.domain.Ministerio;
import br.igreja.escala.ministerio.repository.MembresiaRepository;
import br.igreja.escala.ministerio.repository.MembresiaRepository.MembrosPorMinisterio;
import br.igreja.escala.ministerio.repository.MinisterioRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class MinisterioServiceTest {

    private final MinisterioRepository ministerios = mock(MinisterioRepository.class);
    private final MembresiaRepository membresias = mock(MembresiaRepository.class);
    private final UsuarioService usuarios = mock(UsuarioService.class);
    private final MinisterioService servico = new MinisterioService(ministerios, membresias, usuarios);

    private final Ministerio midia = comId(new Ministerio("Mídia", CorDoMinisterio.MINT, Icone.MONITOR), 1L);
    private final Ministerio louvor = comId(new Ministerio("Louvor", CorDoMinisterio.ROSE, Icone.MUSIC), 2L);

    @Test
    void criaQuandoONomeEstaLivre() {
        when(ministerios.save(any())).thenAnswer(chamada -> chamada.getArgument(0));

        var criado = servico.criar(new DadosDoMinisterio(" Recepção ", CorDoMinisterio.LEMON, Icone.DOOR_OPEN));

        assertThat(criado.getNome()).isEqualTo("Recepção");
        assertThat(criado.getCor()).isEqualTo(CorDoMinisterio.LEMON);
    }

    @Test
    void recusaNomeQueJaExisteNoCampoNome() {
        when(ministerios.existsByNomeIgnoreCase("Mídia")).thenReturn(true);

        assertThatThrownBy(() -> servico.criar(new DadosDoMinisterio("Mídia ", CorDoMinisterio.MINT, Icone.MONITOR)))
                .isInstanceOfSatisfying(RegraVioladaException.class, recusa -> {
                    assertThat(recusa.campo()).isEqualTo("nome");
                    assertThat(recusa.getMessage()).isEqualTo("Já existe um ministério chamado Mídia.");
                });
        verify(ministerios, never()).save(any());
    }

    @Test
    void alteraMantendoOProprioNomeERecusaONomeDeOutro() {
        when(ministerios.findById(1L)).thenReturn(Optional.of(midia));
        when(ministerios.existsByNomeIgnoreCaseAndIdNot("Louvor", 1L)).thenReturn(true);

        servico.alterar(1L, new DadosDoMinisterio("Mídia", CorDoMinisterio.ROSE, Icone.VIDEO));
        assertThat(midia.getCor()).isEqualTo(CorDoMinisterio.ROSE);

        assertThatThrownBy(
                        () -> servico.alterar(1L, new DadosDoMinisterio("Louvor", CorDoMinisterio.ROSE, Icone.VIDEO)))
                .isInstanceOf(RegraVioladaException.class);
        assertThat(midia.getNome()).isEqualTo("Mídia");
    }

    @Test
    void buscarMinisterioQueNaoExisteE404() {
        when(ministerios.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servico.buscar(9L)).isInstanceOf(NaoEncontradoException.class);
    }

    @Test
    void resumosJuntamGerentesEQuantidadeDeMembros() {
        when(ministerios.findAllByOrderByNomeAsc()).thenReturn(List.of(louvor, midia));
        when(membresias.contarMembrosPorMinisterio()).thenReturn(List.of(contagem(1L, 18)));
        var gerente = new Membresia(10L, midia);
        gerente.tornarGerente();
        when(membresias.findByGerenteTrue()).thenReturn(List.of(gerente));
        when(usuarios.resumosPorId(anyCollection()))
                .thenReturn(Map.of(10L, new UsuarioResumo(10L, "Ana Souza", "ana@x.com", null, false, false, true)));

        var resumos = servico.resumos();

        assertThat(resumos)
                .extracting(MinisterioResumo::nome, MinisterioResumo::gerentes, MinisterioResumo::membros)
                .containsExactly(tuple("Louvor", List.of(), 0L), tuple("Mídia", List.of("Ana Souza"), 18L));
        assertThat(resumos.get(0).descricao()).isEqualTo("Sem gerente · 0 membros");
        assertThat(resumos.get(1).descricao()).isEqualTo("Gerente: Ana Souza · 18 membros");
    }

    @Test
    void descricaoNoPluralENoSingular() {
        var resumo = new MinisterioResumo(1L, "Mídia", CorDoMinisterio.MINT, Icone.MONITOR, List.of("Ana", "Bia"), 1);

        assertThat(resumo.descricao()).isEqualTo("Gerentes: Ana, Bia · 1 membro");
    }

    private static MembrosPorMinisterio contagem(Long ministerioId, long total) {
        return new MembrosPorMinisterio() {
            @Override
            public Long getMinisterioId() {
                return ministerioId;
            }

            @Override
            public long getTotal() {
                return total;
            }
        };
    }

    private static Ministerio comId(Ministerio ministerio, Long id) {
        ReflectionTestUtils.setField(ministerio, "id", id);
        return ministerio;
    }
}
