package br.igreja.escala.identidade.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.igreja.escala.compartilhado.RegraVioladaException;
import br.igreja.escala.identidade.domain.Usuario;
import br.igreja.escala.identidade.repository.UsuarioRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

class UsuarioServiceTest {

    /** BCrypt de verdade, com custo baixo para o teste ser rápido. */
    private final PasswordEncoder encoder = new BCryptPasswordEncoder(4);

    private final UsuarioRepository repositorio = mock(UsuarioRepository.class);
    private final ApplicationEventPublisher eventos = mock(ApplicationEventPublisher.class);
    private final UsuarioService usuarios = new UsuarioService(repositorio, encoder, eventos);

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
                        new UsuarioResumo(1L, "Ana Souza", "ana@x.com", null, true, false, true),
                        new UsuarioResumo(2L, "bia Lima", "bia@x.com", null, false, false, true));
        assertThat(usuarios.resumosPorId(List.of(1L, 2L))).containsOnlyKeys(1L, 2L);
    }

    @Test
    void criaContaComSenhaProvisoriaCriptografada() {
        when(repositorio.save(any())).thenAnswer(chamada -> comId(chamada.getArgument(0), 5L));

        var criada = usuarios.criarComSenhaProvisoria(
                new NovoUsuario("Carla Dias", "Carla@X.com", "(11) 90000-0000", "provisoria1"));

        var gravada = ArgumentCaptor.forClass(Usuario.class);
        verify(repositorio).save(gravada.capture());
        assertThat(criada.id()).isEqualTo(5L);
        assertThat(criada.senhaProvisoria()).isTrue();
        assertThat(gravada.getValue().getEmail()).isEqualTo("carla@x.com");
        assertThat(encoder.matches("provisoria1", gravada.getValue().getSenhaHash()))
                .isTrue();
    }

    @Test
    void naoCriaContaComSenhaCurtaNemComEmailQueJaExiste() {
        assertThatThrownBy(() -> usuarios.criarComSenhaProvisoria(new NovoUsuario("Carla", "c@x.com", null, "curta")))
                .isInstanceOfSatisfying(
                        SenhaRecusadaException.class,
                        recusa -> assertThat(recusa.campo()).isEqualTo("senhaProvisoria"));
        when(repositorio.existsByEmail("c@x.com")).thenReturn(true);
        assertThatThrownBy(() ->
                        usuarios.criarComSenhaProvisoria(new NovoUsuario("Carla", "C@x.com", null, "provisoria1")))
                .isInstanceOf(IllegalStateException.class);
        verify(repositorio, never()).save(any());
    }

    @Test
    void redefinirDeixaASenhaProvisoriaDeNovo() {
        var ana = cadastrada(Usuario.membro("Ana", "ana@x.com", encoder.encode("senha-da-ana")));

        usuarios.redefinirSenhaProvisoria(1L, "nova-provisoria");

        assertThat(ana.isSenhaProvisoria()).isTrue();
        assertThat(encoder.matches("nova-provisoria", ana.getSenhaHash())).isTrue();
        assertThatThrownBy(() -> usuarios.redefinirSenhaProvisoria(1L, "curta"))
                .isInstanceOfSatisfying(
                        SenhaRecusadaException.class,
                        recusa -> assertThat(recusa.campo()).isEqualTo("senha"));
    }

    @Test
    void redefinirEncerraAsSessoesSoQuandoASenhaMuda() {
        cadastrada(Usuario.membro("Ana", "ana@x.com", encoder.encode("senha-da-ana")));

        assertThatThrownBy(() -> usuarios.redefinirSenhaProvisoria(1L, "curta"))
                .isInstanceOf(SenhaRecusadaException.class);
        verify(eventos, never()).publishEvent(any(Object.class));

        usuarios.redefinirSenhaProvisoria(1L, "nova-provisoria");
        verify(eventos).publishEvent(new AcessoRevogado(1L));
    }

    @Test
    void trocarAPropriaSenhaNaoRevogaOAcesso() {
        cadastrada(Usuario.comSenhaProvisoria("Ana", "ana@x.com", null, encoder.encode("provisoria1")));

        usuarios.trocarSenha(1L, null, "senha-da-ana");

        verify(eventos, never()).publishEvent(any(Object.class));
    }

    @Test
    void buscaPorIdEPorEmailSemDiferenciarMaiusculas() {
        var ana = cadastrada(Usuario.membro("Ana", "ana@x.com", "hash"));
        when(repositorio.findByEmail("ana@x.com")).thenReturn(Optional.of(ana));

        assertThat(usuarios.buscarPorEmail(" ANA@x.com "))
                .get()
                .extracting(UsuarioResumo::nome)
                .isEqualTo("Ana");
        assertThat(usuarios.buscar(1L).email()).isEqualTo("ana@x.com");
        assertThat(usuarios.buscarPorEmail("ninguem@x.com")).isEmpty();
    }

    @Test
    void editarMudaOsDadosEDizQuaisCamposMudaram() {
        var ana = cadastrada(Usuario.membro("Ana", "ana@x.com", "hash"));
        when(repositorio.findByEmail("ana@x.com")).thenReturn(Optional.of(ana));

        var semMudanca = usuarios.editar(1L, new DadosDaConta(" Ana ", "ANA@x.com", ""));
        assertThat(semMudanca.mudou()).isFalse();

        var editada = usuarios.editar(1L, new DadosDaConta("Ana Souza", "Ana.Souza@X.com", "(11) 98888-7777"));
        assertThat(editada.camposAlterados()).containsExactly("nome", "e-mail", "telefone");
        assertThat(editada.conta())
                .isEqualTo(
                        new UsuarioResumo(1L, "Ana Souza", "ana.souza@x.com", "(11) 98888-7777", false, false, true));
        assertThat(ana.getEmail()).isEqualTo("ana.souza@x.com");
    }

    @Test
    void emailQueJaEOLoginDeOutraContaVoltaNoCampo() {
        var ana = cadastrada(Usuario.membro("Ana", "ana@x.com", "hash"));
        when(repositorio.findByEmail("bia@x.com"))
                .thenReturn(Optional.of(comId(Usuario.membro("Bia", "bia@x.com", "hash"), 2L)));

        assertThatThrownBy(() -> usuarios.editar(1L, new DadosDaConta("Ana", " BIA@x.com", null)))
                .isInstanceOfSatisfying(RegraVioladaException.class, recusa -> {
                    assertThat(recusa.campo()).isEqualTo("email");
                    assertThat(recusa.getMessage()).isEqualTo("O e-mail bia@x.com já é o login de outra conta.");
                });
        assertThatThrownBy(() -> usuarios.editarPropriaConta(1L, new DadosDaConta("Ana", "bia@x.com", null)))
                .isInstanceOf(RegraVioladaException.class);
        assertThat(ana.getEmail()).isEqualTo("ana@x.com");
    }

    @Test
    void editarAPropriaContaDevolveASessaoComONomeEOEmailNovos() {
        cadastrada(Usuario.comSenhaProvisoria("Ana", "ana@x.com", null, "hash"));

        var sessao = usuarios.editarPropriaConta(1L, new DadosDaConta("Ana Souza", "ana.souza@x.com", null));

        assertThat(sessao.getId()).isEqualTo(1L);
        assertThat(sessao.getNome()).isEqualTo("Ana Souza");
        assertThat(sessao.getUsername()).isEqualTo("ana.souza@x.com");
        assertThat(sessao.isSenhaProvisoria()).isTrue();
        verify(eventos, never()).publishEvent(any(Object.class));
    }

    @Test
    void desativarEncerraAsSessoesEReativarNao() {
        var ana = cadastrada(Usuario.membro("Ana", "ana@x.com", "hash"));

        assertThat(usuarios.desativar(1L).ativo()).isFalse();
        assertThat(ana.isAtivo()).isFalse();
        verify(eventos).publishEvent(new AcessoRevogado(1L));

        assertThat(usuarios.reativar(1L).ativo()).isTrue();
        assertThat(ana.isAtivo()).isTrue();
        verify(eventos).publishEvent(any(Object.class));
    }

    private static Usuario comId(Usuario usuario, Long id) {
        ReflectionTestUtils.setField(usuario, "id", id);
        return usuario;
    }

    private Usuario cadastrada(Usuario usuario) {
        comId(usuario, 1L);
        when(repositorio.findById(1L)).thenReturn(Optional.of(usuario));
        return usuario;
    }
}
