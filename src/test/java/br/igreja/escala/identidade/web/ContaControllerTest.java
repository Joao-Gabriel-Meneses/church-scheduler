package br.igreja.escala.identidade.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import br.igreja.escala.TesteDeController;
import br.igreja.escala.identidade.domain.Usuario;
import br.igreja.escala.identidade.domain.UsuarioAutenticado;
import br.igreja.escala.identidade.service.SenhaRecusadaException;
import br.igreja.escala.identidade.service.UsuarioDetailsService;
import br.igreja.escala.identidade.service.UsuarioService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@TesteDeController(ContaController.class)
class ContaControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    UsuarioDetailsService detalhes;

    @MockitoBean
    UsuarioService usuarios;

    private final UsuarioAutenticado comProvisoria =
            autenticado(Usuario.comSenhaProvisoria("Ana", "ana@x.com", null, "hash"));
    private final UsuarioAutenticado semProvisoria = autenticado(Usuario.membro("Bia", "bia@x.com", "hash"));

    @Test
    void exigeLogin() throws Exception {
        mvc.perform(get("/conta/senha")).andExpect(redirectedUrl("/login"));
    }

    @Test
    void comSenhaProvisoriaPedeSoANovaSenha() throws Exception {
        String html = pagina(get("/conta/senha").with(user(comProvisoria)));

        assertThat(html)
                .contains("Crie sua senha", "name=\"novaSenha\"", "name=\"confirmacao\"", "action=\"/logout\"")
                .doesNotContain("name=\"senhaAtual\"");
    }

    @Test
    void semSenhaProvisoriaPedeAAtualEOfereceVoltar() throws Exception {
        String html = pagina(get("/conta/senha").with(user(semProvisoria)));

        assertThat(html).contains("Trocar senha", "name=\"senhaAtual\"", "href=\"/\"");
    }

    @Test
    void salvaAtualizaASessaoEVoltaAoInicio() throws Exception {
        when(usuarios.trocarSenha(1L, null, "senha-da-ana")).thenReturn(semProvisoria);

        mvc.perform(post("/conta/senha")
                        .with(user(comProvisoria))
                        .with(csrf())
                        .param("novaSenha", "senha-da-ana")
                        .param("confirmacao", "senha-da-ana"))
                .andExpect(redirectedUrl("/"))
                .andExpect(flash().attribute("sucesso", "Senha salva"));
    }

    @Test
    void confirmacaoDiferenteVoltaComErroNoCampoESemAsSenhas() throws Exception {
        String html = pagina(post("/conta/senha")
                .with(user(comProvisoria))
                .with(csrf())
                .param("novaSenha", "senha-da-ana")
                .param("confirmacao", "outra-senha"));

        assertThat(html)
                .contains("Repita a nova senha igual nos dois campos.", "aria-invalid=\"true\"")
                .doesNotContain("senha-da-ana", "outra-senha");
        verify(usuarios, never()).trocarSenha(any(), any(), any());
    }

    @Test
    void senhaCurtaVoltaComErroNoCampo() throws Exception {
        String html = pagina(post("/conta/senha")
                .with(user(comProvisoria))
                .with(csrf())
                .param("novaSenha", "curta")
                .param("confirmacao", "curta"));

        assertThat(html).contains("A senha precisa ter de 8 a 64 caracteres.");
    }

    @Test
    void senhaEmBrancoTemUmaMensagemSo() throws Exception {
        String html = pagina(post("/conta/senha")
                .with(user(comProvisoria))
                .with(csrf())
                .param("novaSenha", "")
                .param("confirmacao", ""));

        assertThat(html)
                .containsOnlyOnce("A senha precisa ter de 8 a 64 caracteres.")
                .doesNotContain("Escolha a nova");
    }

    @Test
    void recusaDoServicoApareceNoCampoIndicado() throws Exception {
        when(usuarios.trocarSenha(2L, "errada", "senha-nova-1"))
                .thenThrow(new SenhaRecusadaException("senhaAtual", "A senha atual não confere."));

        String html = pagina(post("/conta/senha")
                .with(user(semProvisoria))
                .with(csrf())
                .param("senhaAtual", "errada")
                .param("novaSenha", "senha-nova-1")
                .param("confirmacao", "senha-nova-1"));

        assertThat(html).contains("id=\"senhaAtual-erro\"", "A senha atual não confere.");
    }

    @Test
    void semCsrfERecusado() throws Exception {
        mvc.perform(post("/conta/senha")
                        .with(user(comProvisoria))
                        .param("novaSenha", "senha-da-ana")
                        .param("confirmacao", "senha-da-ana"))
                .andExpect(status().isForbidden());
    }

    private String pagina(MockHttpServletRequestBuilder requisicao) throws Exception {
        return mvc.perform(requisicao)
                .andExpect(status().isOk())
                .andExpect(view().name("identidade/senha"))
                .andReturn()
                .getResponse()
                .getContentAsString()
                .replaceAll("\\s+", " ");
    }

    private static UsuarioAutenticado autenticado(Usuario usuario) {
        ReflectionTestUtils.setField(usuario, "id", usuario.getNome().equals("Ana") ? 1L : 2L);
        return new UsuarioAutenticado(usuario);
    }
}
