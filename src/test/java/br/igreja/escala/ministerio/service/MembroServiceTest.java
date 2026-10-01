package br.igreja.escala.ministerio.service;

import static br.igreja.escala.ministerio.Exemplos.experiente;
import static br.igreja.escala.ministerio.Exemplos.iniciante;
import static br.igreja.escala.ministerio.Exemplos.midia;
import static br.igreja.escala.ministerio.Exemplos.projecao;
import static br.igreja.escala.ministerio.Exemplos.transmissao;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.igreja.escala.Pessoas;
import br.igreja.escala.compartilhado.NaoEncontradoException;
import br.igreja.escala.compartilhado.RegraVioladaException;
import br.igreja.escala.compartilhado.domain.AcaoAuditada;
import br.igreja.escala.compartilhado.domain.RegistroDeAuditoria;
import br.igreja.escala.compartilhado.service.AuditoriaService;
import br.igreja.escala.identidade.domain.UsuarioAutenticado;
import br.igreja.escala.identidade.service.NovoUsuario;
import br.igreja.escala.identidade.service.SenhaRecusadaException;
import br.igreja.escala.identidade.service.UsuarioResumo;
import br.igreja.escala.identidade.service.UsuarioService;
import br.igreja.escala.ministerio.domain.Habilitacao;
import br.igreja.escala.ministerio.domain.Membresia;
import br.igreja.escala.ministerio.domain.Ministerio;
import br.igreja.escala.ministerio.repository.HabilitacaoRepository;
import br.igreja.escala.ministerio.repository.MembresiaRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.access.AccessDeniedException;

class MembroServiceTest {

    private static final UsuarioAutenticado ADMIN = Pessoas.admin(1L, "Admin");
    private static final UsuarioAutenticado GERENTE = Pessoas.membro(10L, "Gerente");

    private static final UsuarioResumo ANA = new UsuarioResumo(30L, "Ana Souza", "ana@x.com", null, false, false);
    private static final UsuarioResumo BRUNO = new UsuarioResumo(31L, "bruno Lima", "bruno@x.com", null, false, true);

    private final MembresiaRepository membresias = mock(MembresiaRepository.class);
    private final HabilitacaoRepository habilitacoes = mock(HabilitacaoRepository.class);
    private final MinisterioService ministerios = mock(MinisterioService.class);
    private final UsuarioService usuarios = mock(UsuarioService.class);
    private final AuditoriaService auditoria = mock(AuditoriaService.class);
    private final MembroService servico = new MembroService(membresias, habilitacoes, ministerios, usuarios, auditoria);

    private final Ministerio midia = midia();

    @BeforeEach
    void prepara() {
        when(ministerios.buscar(1L)).thenReturn(midia);
        when(usuarios.buscar(30L)).thenReturn(ANA);
    }

    @Test
    void emailNovoCriaAContaComSenhaProvisoriaEAMembresia() {
        when(usuarios.buscarPorEmail("carla@x.com")).thenReturn(Optional.empty());
        var carla = new UsuarioResumo(40L, "Carla Dias", "carla@x.com", null, false, true);
        when(usuarios.criarComSenhaProvisoria(new NovoUsuario("Carla Dias", "carla@x.com", null, "provisoria1")))
                .thenReturn(carla);

        var resultado = servico.cadastrar(1L, new DadosDoMembro("Carla Dias", "carla@x.com", null, "provisoria1"));

        assertThat(resultado).isEqualTo(new ResultadoDoCadastro(carla, false));
        var membresia = ArgumentCaptor.forClass(Membresia.class);
        verify(membresias).save(membresia.capture());
        assertThat(membresia.getValue().getUsuarioId()).isEqualTo(40L);
        assertThat(membresia.getValue().getMinisterio()).isSameAs(midia);
        assertThat(membresia.getValue().isGerente()).isFalse();
    }

    @Test
    void emailQueJaTemContaSoEntraNoMinisterioSemMexerNaConta() {
        when(usuarios.buscarPorEmail("ana@x.com")).thenReturn(Optional.of(ANA));

        var resultado = servico.cadastrar(1L, new DadosDoMembro("Outro nome", "ana@x.com", null, "outrasenha"));

        assertThat(resultado.jaTinhaConta()).isTrue();
        assertThat(resultado.membro()).isEqualTo(ANA);
        verify(membresias).save(any(Membresia.class));
        verify(usuarios, never()).criarComSenhaProvisoria(any());
        verify(usuarios, never()).redefinirSenhaProvisoria(anyLong(), anyString());
    }

    @Test
    void quemJaEstaNoMinisterioNaoEntraDuasVezes() {
        when(usuarios.buscarPorEmail("ana@x.com")).thenReturn(Optional.of(ANA));
        when(membresias.existsByUsuarioIdAndMinisterioId(30L, 1L)).thenReturn(true);

        assertThatThrownBy(() -> servico.cadastrar(1L, new DadosDoMembro("Ana", "ana@x.com", null, "provisoria1")))
                .isInstanceOfSatisfying(RegraVioladaException.class, recusa -> {
                    assertThat(recusa.campo()).isEqualTo("email");
                    assertThat(recusa.getMessage()).isEqualTo("Ana Souza já está neste ministério.");
                });
        verify(membresias, never()).save(any());
    }

    @Test
    void senhaRecusadaPelaIdentidadeViraErroNoCampo() {
        when(usuarios.buscarPorEmail(any())).thenReturn(Optional.empty());
        when(usuarios.criarComSenhaProvisoria(any()))
                .thenThrow(new SenhaRecusadaException("senhaProvisoria", "A senha precisa ter de 8 a 64 caracteres."));

        assertThatThrownBy(() -> servico.cadastrar(1L, new DadosDoMembro("Carla", "c@x.com", null, "curta")))
                .isInstanceOfSatisfying(
                        RegraVioladaException.class,
                        recusa -> assertThat(recusa.campo()).isEqualTo("senhaProvisoria"));
        verify(membresias, never()).save(any());
    }

    @Test
    void listaEmOrdemDeNomeComAsHabilitacoesDeCadaUm() {
        when(membresias.findByMinisterioId(1L)).thenReturn(List.of(new Membresia(31L, midia), gerente(30L)));
        when(usuarios.resumosPorId(anyCollection())).thenReturn(Map.of(30L, ANA, 31L, BRUNO));
        when(habilitacoes.findByFuncaoMinisterioId(1L))
                .thenReturn(List.of(
                        new Habilitacao(30L, transmissao(midia), iniciante(midia)),
                        new Habilitacao(30L, projecao(midia), experiente(midia))));

        var lista = servico.listar(1L);

        assertThat(lista).extracting(MembroResumo::nome).containsExactly("Ana Souza", "bruno Lima");
        assertThat(lista.get(0).gerente()).isTrue();
        assertThat(lista.get(0).descricaoDasHabilitacoes()).isEqualTo("Projeção · Experiente, Transmissão · Iniciante");
        assertThat(lista.get(1).descricaoDasHabilitacoes()).isEqualTo("Sem habilitação ainda");
    }

    @Test
    void pessoaDeOutroMinisterioNaoEEncontrada() {
        when(membresias.findByUsuarioIdAndMinisterioId(30L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servico.buscar(1L, 30L)).isInstanceOf(NaoEncontradoException.class);
        assertThatThrownBy(() -> servico.redefinirSenha(1L, 30L, "nova-senha", GERENTE))
                .isInstanceOf(NaoEncontradoException.class);
        assertThatThrownBy(() -> servico.remover(1L, 30L, GERENTE)).isInstanceOf(NaoEncontradoException.class);
        assertThatThrownBy(() -> servico.tornarGerente(1L, 30L, ADMIN)).isInstanceOf(NaoEncontradoException.class);
        verifyNoInteractions(auditoria);
    }

    @Test
    void gerenteRedefineASenhaDeUmMembroComumERegistraNaAuditoria() {
        when(membresias.findByUsuarioIdAndMinisterioId(30L, 1L)).thenReturn(Optional.of(new Membresia(30L, midia)));

        servico.redefinirSenha(1L, 30L, "nova-provisoria", GERENTE);

        verify(usuarios).redefinirSenhaProvisoria(30L, "nova-provisoria");
        assertThat(auditado())
                .isEqualTo(new RegistroDeAuditoria(
                        AcaoAuditada.REDEFINIR_SENHA,
                        10L,
                        1L,
                        30L,
                        "Senha provisória de Ana Souza redefinida (Mídia)."));
    }

    @Test
    void gerenteNaoRedefineASenhaDeOutroGerenteNemDeAdmin() {
        when(membresias.findByUsuarioIdAndMinisterioId(30L, 1L)).thenReturn(Optional.of(new Membresia(30L, midia)));
        when(membresias.existsByUsuarioIdAndGerenteTrue(30L)).thenReturn(true);

        assertThatThrownBy(() -> servico.redefinirSenha(1L, 30L, "nova-provisoria", GERENTE))
                .hasMessageContaining("só o administrador redefine");

        when(membresias.existsByUsuarioIdAndGerenteTrue(30L)).thenReturn(false);
        when(usuarios.buscar(30L)).thenReturn(new UsuarioResumo(30L, "Ana Souza", "ana@x.com", null, true, false));
        assertThatThrownBy(() -> servico.redefinirSenha(1L, 30L, "nova-provisoria", GERENTE))
                .isInstanceOf(RegraVioladaException.class);

        verify(usuarios, never()).redefinirSenhaProvisoria(anyLong(), anyString());
        verifyNoInteractions(auditoria);
    }

    @Test
    void adminRedefineASenhaDeUmGerente() {
        when(membresias.findByUsuarioIdAndMinisterioId(30L, 1L)).thenReturn(Optional.of(gerente(30L)));
        when(membresias.existsByUsuarioIdAndGerenteTrue(30L)).thenReturn(true);

        servico.redefinirSenha(1L, 30L, "nova-provisoria", ADMIN);

        verify(usuarios).redefinirSenhaProvisoria(30L, "nova-provisoria");
    }

    @Test
    void senhaCurtaNaRedefinicaoNaoMudaNada() {
        when(membresias.findByUsuarioIdAndMinisterioId(30L, 1L)).thenReturn(Optional.of(new Membresia(30L, midia)));
        doThrow(new SenhaRecusadaException("senha", "A senha precisa ter de 8 a 64 caracteres."))
                .when(usuarios)
                .redefinirSenhaProvisoria(30L, "curta");

        assertThatThrownBy(() -> servico.redefinirSenha(1L, 30L, "curta", GERENTE))
                .hasMessage("A senha de Ana Souza não mudou. A senha precisa ter de 8 a 64 caracteres.");
        verifyNoInteractions(auditoria);
    }

    @Test
    void removerApagaAsHabilitacoesDesteMinisterioEAMembresia() {
        var membresia = new Membresia(30L, midia);
        when(membresias.findByUsuarioIdAndMinisterioId(30L, 1L)).thenReturn(Optional.of(membresia));
        var habilitacoesDaAna = List.of(new Habilitacao(30L, projecao(midia), iniciante(midia)));
        when(habilitacoes.findByUsuarioIdAndFuncaoMinisterioId(30L, 1L)).thenReturn(habilitacoesDaAna);

        assertThat(servico.remover(1L, 30L, GERENTE)).isEqualTo(ANA);

        verify(habilitacoes).deleteAll(habilitacoesDaAna);
        verify(membresias).delete(membresia);
        assertThat(auditado().acao()).isEqualTo(AcaoAuditada.REMOVER_MEMBRO);
        assertThat(auditado().descricao()).isEqualTo("Ana Souza saiu do ministério (Mídia).");
    }

    @Test
    void gerenteNaoRemoveOutroGerente() {
        when(membresias.findByUsuarioIdAndMinisterioId(30L, 1L)).thenReturn(Optional.of(gerente(30L)));

        assertThatThrownBy(() -> servico.remover(1L, 30L, GERENTE))
                .hasMessage("Ana Souza continua no ministério: só o administrador remove um gerente.");
        verify(membresias, never()).delete(any());
    }

    @Test
    void soOAdminNomeiaERemoveGerentes() {
        var membresia = new Membresia(30L, midia);
        when(membresias.findByUsuarioIdAndMinisterioId(30L, 1L)).thenReturn(Optional.of(membresia));

        assertThatThrownBy(() -> servico.tornarGerente(1L, 30L, GERENTE)).isInstanceOf(AccessDeniedException.class);
        assertThat(membresia.isGerente()).isFalse();

        servico.tornarGerente(1L, 30L, ADMIN);
        assertThat(membresia.isGerente()).isTrue();
        assertThat(auditado().descricao()).isEqualTo("Ana Souza agora é gerente do ministério (Mídia).");

        assertThatThrownBy(() -> servico.removerGerente(1L, 30L, GERENTE)).isInstanceOf(AccessDeniedException.class);
        servico.removerGerente(1L, 30L, ADMIN);
        assertThat(membresia.isGerente()).isFalse();
    }

    @Test
    void podeMexerNaContaDeMembroComumMasNaoNaDeGerenteOuAdminSalvoSeForAdmin() {
        var comum = new MembroResumo(ANA, false, List.of());
        var admin = new MembroResumo(new UsuarioResumo(2L, "Admin 2", "a2@x.com", null, true, false), false, List.of());
        when(membresias.existsByUsuarioIdAndGerenteTrue(31L)).thenReturn(true);
        var gerenteDeOutro = new MembroResumo(BRUNO, false, List.of());

        assertThat(servico.podeMexerNaConta(comum, GERENTE)).isTrue();
        assertThat(servico.podeMexerNaConta(admin, GERENTE)).isFalse();
        assertThat(servico.podeMexerNaConta(gerenteDeOutro, GERENTE)).isFalse();
        assertThat(servico.podeMexerNaConta(gerenteDeOutro, ADMIN)).isTrue();
    }

    private Membresia gerente(Long usuarioId) {
        var membresia = new Membresia(usuarioId, midia);
        membresia.tornarGerente();
        return membresia;
    }

    private RegistroDeAuditoria auditado() {
        var registro = ArgumentCaptor.forClass(RegistroDeAuditoria.class);
        verify(auditoria, atLeastOnce()).registrar(registro.capture());
        return registro.getValue();
    }
}
