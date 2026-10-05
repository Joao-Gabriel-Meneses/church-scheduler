package br.igreja.escala.escala.web;

import static br.igreja.escala.AcessoDeTeste.GERENTE_DA_MIDIA;
import static br.igreja.escala.AcessoDeTeste.MEMBRO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import br.igreja.escala.AcessoDeTeste;
import br.igreja.escala.TesteDeRotaDoGerente;
import br.igreja.escala.compartilhado.EdicaoConcorrenteException;
import br.igreja.escala.compartilhado.NaoEncontradoException;
import br.igreja.escala.compartilhado.RegraVioladaException;
import br.igreja.escala.escala.domain.TipoDeRegra;
import br.igreja.escala.escala.service.AjusteDaEscala;
import br.igreja.escala.escala.service.Candidato;
import br.igreja.escala.escala.service.ConsultaDaEscala;
import br.igreja.escala.escala.service.VagaEmAjuste;
import br.igreja.escala.escala.solver.ValidacaoDaVaga.Violacao;
import br.igreja.escala.ministerio.Exemplos;
import br.igreja.escala.ministerio.repository.MembresiaRepository;
import br.igreja.escala.ministerio.service.MinisterioService;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@TesteDeRotaDoGerente(VagaController.class)
class VagaControllerTest {

    private static final YearMonth NOVEMBRO = YearMonth.of(2026, 11);

    @Autowired
    MockMvc mvc;

    @Autowired
    MembresiaRepository membresias;

    @MockitoBean
    AjusteDaEscala ajuste;

    @MockitoBean
    ConsultaDaEscala consulta;

    @MockitoBean
    MinisterioService ministerios;

    @BeforeEach
    void prepara() {
        AcessoDeTeste.configurar(membresias);
        when(ministerios.buscar(AcessoDeTeste.MIDIA)).thenReturn(Exemplos.midia());
        when(ajuste.abrir(1L, 8L)).thenReturn(vagaVazia());
        when(ajuste.abrir(1L, 99L)).thenThrow(new NaoEncontradoException("Vaga 99"));
        when(consulta.doMes(1L, NOVEMBRO)).thenReturn(PaginasDeExemplo.rascunho(NOVEMBRO, true, true));
    }

    @Nested
    class Autorizacao {

        @Test
        void membroComumNaoAbreNemMudaVaga() throws Exception {
            mvc.perform(get("/ministerios/1/escalas/vagas/8").with(user(MEMBRO)))
                    .andExpect(status().isForbidden());
            for (var acao : todasAsAcoes(1, 8)) {
                mvc.perform(acao.with(user(MEMBRO))).andExpect(status().isForbidden());
            }
            verifyNoInteractions(ajuste);
        }

        @Test
        void gerenteDaMidiaNaoMexeNaEscalaDoLouvorNemPorPostDireto() throws Exception {
            mvc.perform(get("/ministerios/2/escalas/vagas/8").with(user(GERENTE_DA_MIDIA)))
                    .andExpect(status().isForbidden());
            for (var acao : todasAsAcoes(2, 8)) {
                mvc.perform(acao.with(user(GERENTE_DA_MIDIA))).andExpect(status().isForbidden());
            }
            verifyNoInteractions(ajuste);
        }

        @Test
        void vagaDeOutroMinisterioE404() throws Exception {
            when(ajuste.esvaziar(1L, 99L, 0, GERENTE_DA_MIDIA)).thenThrow(new NaoEncontradoException("Vaga 99"));

            mvc.perform(get("/ministerios/1/escalas/vagas/99").with(user(GERENTE_DA_MIDIA)))
                    .andExpect(status().isNotFound());
            mvc.perform(acao(1, 99, "esvaziar").with(user(GERENTE_DA_MIDIA))).andExpect(status().isNotFound());
        }

        @Test
        void semCsrfERecusado() throws Exception {
            mvc.perform(post("/ministerios/1/escalas/vagas/8/escalar")
                            .param("usuarioId", "33")
                            .param("versao", "0")
                            .param("mes", "2026-11")
                            .with(user(GERENTE_DA_MIDIA)))
                    .andExpect(status().isForbidden());
            verify(ajuste, never()).escalar(anyLong(), anyLong(), anyLong(), any(), anyLong(), any());
        }
    }

    @Test
    void comHtmxAVagaVaiParaOSheetComOsCandidatosEmGrupos() throws Exception {
        String html = mvc.perform(get("/ministerios/1/escalas/vagas/8")
                        .header("HX-Request", "true")
                        .with(user(GERENTE_DA_MIDIA)))
                .andExpect(status().isOk())
                .andExpect(view().name(VagaController.CONTEUDO))
                .andReturn()
                .getResponse()
                .getContentAsString()
                .replaceAll("\\s+", " ");

        assertThat(html)
                .doesNotContain("<html")
                .contains("id=\"vaga-titulo\" hx-swap-oob=\"true\"", "Transmissão, 01/11 · Dom · 18h00")
                .contains("Vaga vazia", "Ninguém habilitado em Transmissão marcou Pode.")
                .contains("Podem servir", "Diego Martins", "Iniciante · nenhuma escala no mês")
                .contains("Só com justificativa", "Carla Dias", "Não marcou Pode neste evento.")
                .contains("Regra: DISPONIBILIDADE", "name=\"justificativa\"", "Forçar")
                .contains("Não podem", "Ana Souza", "Já serve em Projeção neste evento.", "UMA_FUNCAO_POR_EVENTO")
                .contains("hx-post=\"/ministerios/1/escalas/vagas/8/escalar\"", "hx-target=\"#escala\"")
                .contains("name=\"versao\" value=\"4\"", "name=\"mes\" value=\"2026-11\"")
                .doesNotContain("rt-btn--primary");
        assertThat(html.split("action=\"/ministerios/1/escalas/vagas/8/escalar\"", -1))
                .as("um formulário para quem pode e outro para quem só entra forçado; quem não pode não tem botão")
                .hasSize(3);
    }

    @Test
    void semJsAVagaEUmaPagina() throws Exception {
        String html = mvc.perform(get("/ministerios/1/escalas/vagas/8").with(user(GERENTE_DA_MIDIA)))
                .andExpect(status().isOk())
                .andExpect(view().name(VagaController.PAGINA))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(html)
                .contains("<h1 class=\"text-title\">Transmissão, 01/11 · Dom · 18h00</h1>")
                .contains("href=\"/ministerios/1/escalas?mes=2026-11\"")
                .doesNotContain("hx-swap-oob");
    }

    @Test
    void escalarComHtmxDevolveARegiaoComOToast() throws Exception {
        when(ajuste.escalar(1L, 8L, 33L, null, 4, GERENTE_DA_MIDIA))
                .thenReturn("Diego Martins está em Transmissão, 01/11 · Dom");

        String html = mvc.perform(escalar(33, null).header("HX-Request", "true").with(user(GERENTE_DA_MIDIA)))
                .andExpect(status().isOk())
                .andExpect(view().name(VagaController.REGIAO))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(html)
                .contains("id=\"escala\"", "hx-swap-oob=\"beforeend:#toasts\"")
                .contains("Diego Martins está em Transmissão, 01/11 · Dom")
                .contains("<table class=\"rt-table rt-sched\">", "id=\"vaga-sheet\"")
                .contains("hx-get=\"/ministerios/1/escalas/vagas/7?mes=2026-11\"");
    }

    @Test
    void escalarSemJsVoltaParaAEscalaComOAviso() throws Exception {
        when(ajuste.escalar(1L, 8L, 33L, null, 4, GERENTE_DA_MIDIA))
                .thenReturn("Diego Martins está em Transmissão, 01/11 · Dom");

        mvc.perform(escalar(33, null).with(user(GERENTE_DA_MIDIA)))
                .andExpect(redirectedUrl("/ministerios/1/escalas?mes=2026-11"))
                .andExpect(flash().attribute("sucesso", "Diego Martins está em Transmissão, 01/11 · Dom"));
    }

    @Test
    void forcarSemJustificativaVoltaParaOSheetComOErroNoCampoDaPessoa() throws Exception {
        when(ajuste.escalar(1L, 8L, 32L, "", 4, GERENTE_DA_MIDIA))
                .thenThrow(new RegraVioladaException(
                        "justificativa", "Para escalar Carla Dias mesmo assim, escreva a justificativa."));

        String html = mvc.perform(escalar(32, "").header("HX-Request", "true").with(user(GERENTE_DA_MIDIA)))
                .andExpect(status().isOk())
                .andExpect(view().name(VagaController.CONTEUDO))
                .andExpect(header().string("HX-Retarget", "#vaga-conteudo"))
                .andExpect(header().string("HX-Reswap", "innerHTML"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(html)
                .contains(
                        "id=\"justificativa-32-erro\"",
                        "Para escalar Carla Dias mesmo assim, escreva a justificativa.");
    }

    @Test
    void regraQueNuncaSeForcaPorPostDiretoVoltaComOMotivo() throws Exception {
        when(ajuste.escalar(1L, 8L, 30L, "Preciso dela", 4, GERENTE_DA_MIDIA))
                .thenThrow(RegraVioladaException.geral("Ana Souza não pode servir em Transmissão. Já serve em Projeção"
                        + " neste evento. Regra: UMA_FUNCAO_POR_EVENTO"));

        mvc.perform(escalar(30, "Preciso dela").with(user(GERENTE_DA_MIDIA)))
                .andExpect(redirectedUrl("/ministerios/1/escalas/vagas/8"))
                .andExpect(flash().attribute(
                                "recusa",
                                "Ana Souza não pode servir em Transmissão. Já serve em Projeção neste evento. Regra:"
                                        + " UMA_FUNCAO_POR_EVENTO"));
        String html = mvc.perform(
                        escalar(30, "Preciso dela").header("HX-Request", "true").with(user(GERENTE_DA_MIDIA)))
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(html).contains("class=\"rt-alert\"", "Regra: UMA_FUNCAO_POR_EVENTO");
    }

    @Test
    void vagaQueMudouEmOutraAbaRecarregaAGradeComOAviso() throws Exception {
        when(ajuste.esvaziar(1L, 8L, 0, GERENTE_DA_MIDIA))
                .thenThrow(new EdicaoConcorrenteException("mudou"))
                .thenThrow(new ObjectOptimisticLockingFailureException("Vaga", 8L));

        for (int vez = 0; vez < 2; vez++) {
            String html = mvc.perform(
                            acao(1, 8, "esvaziar").header("HX-Request", "true").with(user(GERENTE_DA_MIDIA)))
                    .andExpect(status().isOk())
                    .andExpect(view().name(VagaController.REGIAO))
                    .andReturn()
                    .getResponse()
                    .getContentAsString();
            assertThat(html).contains("id=\"escala\"", VagaController.MUDOU_EM_OUTRA_ABA);
        }
        when(ajuste.fixar(1L, 8L, 0, GERENTE_DA_MIDIA)).thenThrow(new EdicaoConcorrenteException("mudou"));
        mvc.perform(acao(1, 8, "fixar").with(user(GERENTE_DA_MIDIA)))
                .andExpect(redirectedUrl("/ministerios/1/escalas?mes=2026-11"))
                .andExpect(flash().attribute("recusa", VagaController.MUDOU_EM_OUTRA_ABA));
    }

    @Test
    void fixarEDesafixarPassamAVersaoQueATelaViu() throws Exception {
        when(ajuste.fixar(1L, 8L, 0, GERENTE_DA_MIDIA)).thenReturn("Vaga fixada");
        when(ajuste.desafixar(1L, 8L, 0, GERENTE_DA_MIDIA)).thenReturn("Vaga solta");

        mvc.perform(acao(1, 8, "fixar").with(user(GERENTE_DA_MIDIA)))
                .andExpect(flash().attribute("sucesso", "Vaga fixada"));
        mvc.perform(acao(1, 8, "desafixar").with(user(GERENTE_DA_MIDIA)))
                .andExpect(flash().attribute("sucesso", "Vaga solta"));
    }

    private static VagaEmAjuste vagaVazia() {
        return new VagaEmAjuste(
                1L,
                8L,
                4,
                NOVEMBRO,
                "Transmissão",
                "01/11 · Dom · 18h00",
                "Culto de domingo",
                null,
                false,
                false,
                null,
                List.of(),
                "Ninguém habilitado em Transmissão marcou Pode.",
                null,
                List.of(
                        new Candidato(33L, "Diego Martins", "DM", "Iniciante", 0, List.of(), false),
                        new Candidato(
                                32L,
                                "Carla Dias",
                                "CD",
                                "Experiente",
                                1,
                                List.of(new Violacao(TipoDeRegra.DISPONIBILIDADE, "Não marcou Pode neste evento.")),
                                false),
                        new Candidato(
                                30L,
                                "Ana Souza",
                                "AS",
                                "Experiente",
                                1,
                                List.of(new Violacao(
                                        TipoDeRegra.UMA_FUNCAO_POR_EVENTO, "Já serve em Projeção neste evento.")),
                                false)));
    }

    private static MockHttpServletRequestBuilder escalar(long usuarioId, String justificativa) {
        var requisicao = post("/ministerios/1/escalas/vagas/8/escalar")
                .param("usuarioId", String.valueOf(usuarioId))
                .param("versao", "4")
                .param("mes", "2026-11")
                .with(csrf());
        return justificativa == null ? requisicao : requisicao.param("justificativa", justificativa);
    }

    private static MockHttpServletRequestBuilder acao(long ministerioId, long vagaId, String acao) {
        return post("/ministerios/{m}/escalas/vagas/{v}/{a}", ministerioId, vagaId, acao)
                .param("usuarioId", "33")
                .param("versao", "0")
                .param("mes", "2026-11")
                .with(csrf());
    }

    private static List<MockHttpServletRequestBuilder> todasAsAcoes(long ministerioId, long vagaId) {
        return List.of(
                acao(ministerioId, vagaId, "escalar").param("justificativa", "Motivo"),
                acao(ministerioId, vagaId, "esvaziar"),
                acao(ministerioId, vagaId, "fixar"),
                acao(ministerioId, vagaId, "desafixar"));
    }
}
