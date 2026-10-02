package br.igreja.escala.compartilhado.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.igreja.escala.TesteDeController;
import br.igreja.escala.disponibilidade.domain.Resposta;
import br.igreja.escala.disponibilidade.service.LinhaDeDisponibilidade;
import br.igreja.escala.escala.service.CelulaDaGrade;
import br.igreja.escala.escala.service.LinhaDaGrade;
import br.igreja.escala.escala.service.SlotDaGrade;
import br.igreja.escala.identidade.domain.Usuario;
import br.igreja.escala.identidade.domain.UsuarioAutenticado;
import br.igreja.escala.identidade.service.UsuarioDetailsService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.stereotype.Controller;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.ui.Model;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Marcação dos fragmentos de templates/componentes, renderizados pelo template de teste teste/fragmentos.html. O HTML
 * tem os espaços e quebras de linha reduzidos a um espaço.
 */
@TesteDeController(ComponentesTest.Pagina.class)
@Import(ComponentesTest.Pagina.class)
class ComponentesTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    UsuarioDetailsService usuarios;

    private String html;

    @BeforeEach
    void renderiza() throws Exception {
        var ana = new UsuarioAutenticado(Usuario.membro("Ana", "ana@x.com", "hash"));
        html = mvc.perform(get("/teste/fragmentos").with(user(ana)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString()
                .replaceAll("\\s+", " ");
    }

    @Test
    void iconeUsaOSpriteVersionadoComTracoDeUmEMeio() {
        assertThat(secao("botao-icone"))
                .contains("stroke-width=\"1.5\"", "aria-hidden=\"true\"")
                .containsPattern("<use href=\"/icones/lucide-[0-9a-f]+\\.svg#lock\">");
    }

    @Test
    void botaoPadraoEPrimarioEEnviaOFormulario() {
        assertThat(secao("botao-padrao"))
                .contains("<button type=\"submit\" class=\"rt-btn rt-btn--primary\">")
                .contains("Salvar")
                .doesNotContain("<svg");
    }

    @Test
    void botaoComHrefViraLinkComIconeEAjustesDeLayout() {
        assertThat(secao("botao-link"))
                .contains("<a href=\"/escalas\" class=\"rt-btn rt-btn--primary w-full justify-center\">")
                .contains("#sparkles\"")
                .doesNotContain("<button");
    }

    @Test
    void botaoPequenoEscuroComIconeDepoisDoTexto() {
        String botao = secao("botao-pequeno");

        assertThat(botao).contains("<button type=\"button\" class=\"rt-btn rt-btn--dark rt-btn--sm\">");
        assertThat(botao.indexOf("Preencher vaga")).isLessThan(botao.indexOf("#chevron-right"));
    }

    @Test
    void botaoDeIconeTemRotuloAcessivel() {
        assertThat(secao("botao-icone"))
                .contains("<button type=\"button\" class=\"rt-icon-btn\" aria-label=\"Travar disponibilidade\""
                        + " title=\"Travar disponibilidade\">");
        assertThat(secao("botao-icone-link"))
                .contains(
                        "<a href=\"/\" class=\"rt-icon-btn rt-icon-btn--dark rt-icon-btn--lg\" aria-label=\"Escalas\"");
    }

    @Test
    void badgeTravadaTemIconeEBadgeDeMinisterioUsaOTint() {
        assertThat(secao("badge-neutra")).contains("<span class=\"rt-badge\">Experiente</span>");
        assertThat(secao("badge-travada")).contains("class=\"rt-badge rt-badge--locked\"", "#lock\"", "Travada");
        assertThat(secao("badge-ministerio")).contains("class=\"rt-badge bg-tint-mint\"");
    }

    @Test
    void linhaComHrefEClicavelESelecionada() {
        assertThat(secao("linha-link"))
                .contains("<a href=\"/funcoes/1\" class=\"rt-list-row rt-list-row--tint\" aria-current=\"true\">")
                .contains("rt-list-row__icon", "#monitor\"", "Experiente · 3 escalas em outubro", "#chevron-right\"");
        assertThat(secao("linha-simples"))
                .contains("<div class=\"rt-list-row\">", "Culto de quinta")
                .doesNotContain("rt-list-row__icon", "rt-list-row__sub", "chevron-right");
    }

    @Test
    void celulasDaTabela() {
        assertThat(secao("tabela"))
                .contains("<span class=\"rt-avatar\" aria-hidden=\"true\">AS</span>", "Ana Souza")
                .contains("<div class=\"rt-cell-2\">", "Projeção · Experiente");
        assertThat(entre("<td id=\"estado-ok\">", "</td>")).contains("<span class=\"rt-dot\"></span>Respondeu");
        assertThat(entre("<td id=\"estado-alerta\">", "</td>"))
                .contains("rt-badge--alert", "#triangle-alert\"", "Sem resposta")
                .doesNotContain("rt-dot");
        assertThat(entre("<td id=\"com-icone\">", "</td>"))
                .contains("<span class=\"rt-route__thumb\" aria-hidden=\"true\">", "#monitor\"")
                .contains("<span class=\"rt-route__main\">Projeção</span>");
    }

    @Test
    void alertaDizOQueOndeEPorQueETemAcao() {
        assertThat(secao("alerta"))
                .contains("<div class=\"rt-alert\" role=\"alert\">", "#triangle-alert\"")
                .contains("rt-alert__body", "<p class=\"rt-alert__rule\">Regra: LIMITE_POR_PERIODO</p>")
                .contains("<a href=\"/vagas/7\" class=\"rt-btn rt-btn--dark rt-btn--sm\">", "Preencher");
        assertThat(secao("alerta-simples"))
                .contains("E-mail ou senha inválidos.")
                .doesNotContain("rt-alert__body", "rt-alert__rule", "rt-btn");
    }

    @Test
    void campoTemRotuloLigadoEAtributosDoNavegador() {
        assertThat(secao("campo"))
                .contains("<label class=\"rt-field__label\" for=\"email\">E-mail</label>")
                .contains("<input class=\"rt-input\" id=\"email\" name=\"email\" type=\"email\"")
                .contains("required=\"required\"", "autofocus=\"autofocus\"", "autocomplete=\"username\"")
                .doesNotContain("aria-invalid", "aria-describedby", "value=", "placeholder=", "rt-field__error");
    }

    @Test
    void campoComErroMarcaInvalidoELigaDicaEErro() {
        assertThat(secao("campo-com-erro"))
                .contains("value=\"11\"", "aria-invalid=\"true\"")
                .contains("aria-describedby=\"telefone-dica telefone-erro\"")
                .contains("<p class=\"rt-field__hint\" id=\"telefone-dica\">Opcional. Só o gerente vê.</p>")
                .contains("<p class=\"rt-field__error\" id=\"telefone-erro\">", "#triangle-alert\"")
                .contains("Informe o DDD e o número.");
    }

    @Test
    void campoDoObjetoLeValorEErrosDoFormulario() {
        assertThat(secao("objeto-nome"))
                .contains("name=\"nome\"", "value=\"Ana Souza\"")
                .doesNotContain("aria-invalid");
        assertThat(secao("objeto-email"))
                .contains("value=\"ana@exemplo\"", "aria-invalid=\"true\"", "aria-describedby=\"email-erro\"")
                .contains("Informe um e-mail válido, como ana@exemplo.com.");
    }

    @Test
    void selecaoDoObjetoMarcaAOpcaoAtual() {
        assertThat(secao("objeto-funcao"))
                .contains("<select class=\"rt-input\" id=\"funcao\" name=\"funcao\">")
                .contains("<option value=\"\">Escolha a função</option>")
                .contains("<option value=\"1\">Projeção</option>")
                .contains("<option value=\"2\" selected=\"selected\">Transmissão</option>")
                .contains("#chevron-down\"");
    }

    @Test
    void caixaDeSelecaoDoObjetoMandaOMarcadorDoSpring() {
        assertThat(secao("objeto-gerente"))
                .contains("<label class=\"rt-check\">")
                .contains("type=\"checkbox\" id=\"gerente\" name=\"gerente\" value=\"true\" checked=\"checked\"")
                .contains("<input type=\"hidden\" name=\"_gerente\" value=\"on\">");
        assertThat(secao("caixa"))
                .contains("<input type=\"checkbox\" id=\"lembrar\" name=\"lembrar\">")
                .contains("Continuar conectado neste aparelho");
    }

    @Test
    void toastTemBotaoDeFecharEVersaoOob() {
        assertThat(secao("toast"))
                .contains("<div class=\"rt-toast\" data-toast>", "#circle-check\"", "Função Projeção criada")
                .contains("aria-label=\"Fechar aviso\"", "data-fechar-toast");
        assertThat(secao("toast-oob"))
                .contains("<div hx-swap-oob=\"beforeend:#toasts\"><div class=\"rt-toast\" data-toast>");
    }

    @Test
    void navegacaoMarcaAPaginaAtual() {
        assertThat(secao("nav"))
                .contains("<nav class=\"rt-nav flex-wrap\" aria-label=\"Principal\">")
                .contains("<a class=\"rt-nav__item\" href=\"/\" aria-current=\"page\">", "Minhas escalas")
                .contains("<a class=\"rt-nav__item\" href=\"/disponibilidade\">", "#calendar\"");
        assertThat(secao("barra")).contains("Disponib.").doesNotContain("Disponibilidade<");
    }

    @Test
    void railSoApareceComMaisDeUmMinisterio() {
        assertThat(secao("rail")).contains("<nav class=\"rt-rail\" aria-label=\"Ministérios\">");
        assertThat(secao("rail-unica")).doesNotContain("<nav");
    }

    @Test
    void cadaItemDaRailTemNomeAcessivelETooltipSemTitle() {
        assertThat(secao("rail"))
                .contains("class=\"rt-icon-btn rt-com-tooltip\" href=\"/ministerios/1\" aria-label=\"Mídia\""
                        + " aria-current=\"page\"")
                .contains("<span class=\"rt-tooltip\" aria-hidden=\"true\">Mídia</span>")
                .contains("aria-label=\"Louvor\"", "<span class=\"rt-tooltip\" aria-hidden=\"true\">Louvor</span>")
                .doesNotContain("title=");
    }

    @Test
    void seletorDeMinisterioMostraOAtualEListaOsOutros() {
        assertThat(secao("seletor"))
                .contains("<summary class=\"rt-nav__item cursor-pointer\" aria-label=\"Trocar de ministério\">Mídia")
                .contains("<a href=\"/ministerios/2\" class=\"rt-list-row\">", "Louvor");
    }

    @Test
    void toolbarTemSeletorDePeriodoComRotuloLongoECurto() {
        assertThat(secao("toolbar"))
                .contains("<div id=\"barra\" class=\"rt-toolbar\">", "<div class=\"rt-seg\">")
                .contains("href=\"/escalas/2026-09\" aria-label=\"Mês anterior\"")
                .contains("href=\"/escalas/2026-11\" aria-label=\"Próximo mês\"")
                .contains("<span class=\"rt-toolbar__longo\">Outubro 2026</span>")
                .contains("<span class=\"rt-toolbar__curto\">Out 2026</span>");
    }

    @Test
    void toolbarAbreAsDemaisAcoesNumSheetNoCelular() {
        assertThat(secao("toolbar"))
                .contains("class=\"rt-icon-btn rt-toolbar__mais\" popovertarget=\"acoes-da-barra\"")
                .contains("aria-label=\"Mais ações\"", "#ellipsis\"")
                .contains("<div id=\"acoes-da-barra\" popover class=\"rt-sheet rt-toolbar__acoes\" role=\"dialog\"")
                .contains("aria-label=\"Ações de Outubro 2026\"")
                .contains("popovertargetaction=\"hide\" aria-label=\"Fechar\"");
    }

    @Test
    void toolbarCompletaTemTravaRegrasEAAcaoUmaVezSo() {
        String toolbar = secao("toolbar");

        assertThat(toolbar)
                .contains("class=\"rt-badge rt-badge--locked\"", "Disponibilidade travada")
                .contains("<a class=\"rt-icon-btn rt-toolbar__regras\" href=\"/regras\" aria-label=\"Regras\"")
                .contains("<span class=\"rt-toolbar__rotulo\">Regras</span>")
                .contains("<button type=\"button\" class=\"rt-btn rt-btn--primary\">", "#sparkles\"")
                .doesNotContain("Disponibilidade aberta");
        assertThat(toolbar.split("Gerar escala", -1)).hasSize(2);
    }

    @Test
    void botaoForaDoFormularioEnviaOFormularioPeloId() {
        assertThat(secao("botao-formulario"))
                .contains("<button type=\"submit\" class=\"rt-btn rt-btn--dark\" form=\"travar-disponibilidade\">")
                .contains("#lock\"", "Travar disponibilidade");
        assertThat(secao("botao-padrao")).doesNotContain("form=", "data-copiar");
    }

    @Test
    void botaoDeCopiarIndicaOTextoQueCopia() {
        assertThat(secao("botao-copiar"))
                .contains("<button type=\"button\" class=\"rt-btn rt-btn--dark\" data-copiar=\"texto-do-lembrete\">")
                .contains("#copy\"", "Copiar texto");
    }

    @Test
    void statCardTemRotuloValorContextoESetaParaODetalhe() {
        assertThat(secao("estatistica"))
                .contains("<div class=\"rt-panel rt-stat\">", "<span class=\"rt-label\">Sem resposta</span>")
                .contains("<div class=\"rt-stat__value\">5</div>", "<div class=\"rt-stat__sub\">de 20 membros</div>")
                .contains(
                        "<a href=\"#respostas\" class=\"rt-icon-btn rt-icon-btn--sm\" aria-label=\"Ver Sem resposta\"")
                .contains("#arrow-up-right\"");
        assertThat(secao("estatistica-simples"))
                .contains("<div class=\"rt-stat__value\">9</div>")
                .doesNotContain("rt-stat__sub", "rt-icon-btn");
    }

    @Test
    void toolbarComFormularioEnviaAAcaoPorPost() {
        assertThat(secao("toolbar-com-formulario"))
                .contains("<button type=\"submit\" class=\"rt-btn rt-btn--dark\" form=\"travar-disponibilidade\">")
                .contains("Disponibilidade aberta");
        assertThat(secao("toolbar")).contains("<button type=\"button\" class=\"rt-btn rt-btn--primary\">");
    }

    @Test
    void linhaDeDisponibilidadeEnviaARespostaPorHtmxComEstadoEmTexto() {
        String linha = secao("disponibilidade");

        assertThat(linha)
                .contains("<div class=\"rt-avail__row\" id=\"evento-500\">")
                .contains("<span class=\"rt-avail__day\">01</span><span class=\"rt-caption\">Dom</span>")
                .contains("<span>Culto de domingo</span> <span class=\"rt-caption\">18h00</span>")
                .contains("<form class=\"rt-choice\" role=\"group\""
                        + " aria-label=\"Disponibilidade em 01/11 · Dom · Culto de domingo, 18h00\" method=\"post\""
                        + " action=\"/disponibilidade/ministerios/1/eventos/500?mes=2026-11\""
                        + " hx-post=\"/disponibilidade/ministerios/1/eventos/500?mes=2026-11\" hx-target=\"#grupo-1\""
                        + " hx-swap=\"outerHTML\">")
                .contains("<button type=\"submit\" name=\"resposta\" value=\"PODE\" class=\"is-yes\" id=\"pode-500\""
                        + " aria-pressed=\"true\" aria-label=\"Pode — 01/11 · Dom · Culto de domingo, 18h00\">")
                .contains("<button type=\"submit\" name=\"resposta\" value=\"NAO_PODE\" class=\"is-no\""
                        + " id=\"nao-pode-500\" aria-pressed=\"false\"")
                .contains("#check\"", "#x\"")
                .doesNotContain("PREFERE_NAO", "is-maybe", "disabled", "Marcado por", "rt-avail__aviso");
    }

    @Test
    void linhaTravadaDesativaOsBotoesEMostraQuemMarcouEOAviso() {
        String linha = secao("disponibilidade-travada");

        assertThat(linha)
                .contains("aria-pressed=\"false\"", "disabled=\"disabled\"")
                .contains("<span class=\"rt-caption\">Marcado por Paula Ribeiro</span>")
                .contains("<span class=\"rt-avail__aviso\">", "#triangle-alert\"")
                .contains("O horário mudou (era 18h00). Toque de novo para confirmar.");
        assertThat(linha.split("disabled=\"disabled\"", -1)).hasSize(3);
    }

    @Test
    void seletorDePeriodoSozinhoTemOsDoisRotulos() {
        assertThat(secao("seletor-de-periodo"))
                .contains("<div class=\"rt-seg\">")
                .contains("href=\"/disponibilidade?mes=2026-10\" aria-label=\"Mês anterior\"")
                .contains("href=\"/disponibilidade?mes=2026-12\" aria-label=\"Próximo mês\"")
                .contains("<span class=\"rt-toolbar__longo\">Novembro 2026</span>")
                .contains("<span class=\"rt-toolbar__curto\">Nov 2026</span>")
                .doesNotContain("rt-toolbar__mais");
    }

    @Test
    void toolbarMinimaSoTemPeriodoETravaAbertaNoSheet() {
        assertThat(secao("toolbar-minima"))
                .contains("class=\"rt-badge rt-toolbar__so-sheet\"", "Disponibilidade aberta")
                .doesNotContain("rt-toolbar__regras", "rt-btn", "rt-badge--locked");
    }

    @Test
    void toolbarEmAndamentoMostraOProgressoNoLugarDaAcao() {
        assertThat(secao("toolbar-em-andamento"))
                .contains("<span class=\"rt-badge\">", "#sparkles\"", "Gerando escala")
                .doesNotContain("rt-btn");
    }

    @Test
    void gradeTemUmaLinhaPorEventoEUmaColunaPorFuncao() {
        String grade = secao("grade");

        assertThat(grade)
                .contains("<table class=\"rt-table rt-sched\">", "<th scope=\"col\">Evento</th>")
                .contains("<th scope=\"col\">Projeção</th>", "<th scope=\"col\">Transmissão</th>")
                .contains("<span class=\"rt-avail__day\">05</span><span class=\"rt-caption\">Dom</span>")
                .contains("<span>Culto de domingo</span><span class=\"rt-caption\">18h00</span>")
                .contains("<ul class=\"flex flex-col gap-3 md:hidden\" aria-label=\"Escala do mês\">")
                .contains("<div class=\"rt-panel relative hidden overflow-x-auto md:block\">");
        assertThat(grade.split("<tr class=\"is-alert\">", -1))
                .as("só a linha com vaga vazia")
                .hasSize(2);
    }

    @Test
    void slotMostraOEstadoComTextoOuIconeAlemDaCor() {
        String grade = secao("grade");

        assertThat(grade)
                .contains("<span class=\"rt-slot\"><span class=\"rt-avatar\" aria-hidden=\"true\">AS</span><span>Ana"
                        + " Souza<span class=\"rt-slot__meta\">Experiente</span></span></span>")
                .contains("<span class=\"rt-slot rt-slot--pinned\">", "#pin\"")
                .contains("<span class=\"rt-slot rt-slot--forced\">", "<span class=\"rt-slot__meta\">Forçada</span>")
                .contains("<span class=\"rt-slot rt-slot--empty\">", "Vaga vazia", "Opcional, vazia")
                .contains("<span class=\"rt-slot__meta\">Sem habilitação</span>")
                .contains("<span aria-hidden=\"true\">—</span><span class=\"sr-only\">Não precisa</span>");
    }

    private String secao(String id) {
        return entre("<section id=\"" + id + "\">", "</section>");
    }

    private String entre(String inicio, String fim) {
        int de = html.indexOf(inicio);
        assertThat(de).as("%s no HTML", inicio).isNotNegative();
        return html.substring(de, html.indexOf(fim, de));
    }

    /** Formulário de exemplo, como os DTOs da Fase 1 (record). */
    record Exemplo(String nome, String email, Long funcao, boolean gerente) {}

    @Controller
    static class Pagina {

        @GetMapping("/teste/fragmentos")
        String fragmentos(Model model) {
            var form = new Exemplo("Ana Souza", "ana@exemplo", 2L, true);
            BindingResult resultado = new BeanPropertyBindingResult(form, "form");
            resultado.rejectValue("email", "Email", "Informe um e-mail válido, como ana@exemplo.com.");
            model.addAttribute("form", form);
            model.addAttribute(BindingResult.MODEL_KEY_PREFIX + "form", resultado);
            model.addAttribute("opcoes", List.of(new Opcao("1", "Projeção"), new Opcao("2", "Transmissão")));
            model.addAttribute("funcoesDaGrade", List.of("Projeção", "Transmissão"));
            model.addAttribute(
                    "linhasDaGrade",
                    List.of(
                            new LinhaDaGrade(
                                    "05",
                                    "Dom",
                                    "Culto de domingo",
                                    "18h00",
                                    false,
                                    List.of(
                                            new CelulaDaGrade(
                                                    "Projeção",
                                                    true,
                                                    List.of(SlotDaGrade.de("Ana Souza", "Experiente", false, false))),
                                            new CelulaDaGrade(
                                                    "Transmissão",
                                                    true,
                                                    List.of(SlotDaGrade.de(
                                                            "Pedro Alves", "Experiente", true, false))))),
                            new LinhaDaGrade(
                                    "12",
                                    "Dom",
                                    "Culto de domingo",
                                    "18h00",
                                    true,
                                    List.of(
                                            new CelulaDaGrade(
                                                    "Projeção",
                                                    true,
                                                    List.of(SlotDaGrade.de("Carla Dias", "Iniciante", false, true))),
                                            new CelulaDaGrade(
                                                    "Transmissão",
                                                    true,
                                                    List.of(SlotDaGrade.vazia(true), SlotDaGrade.vazia(false))))),
                            new LinhaDaGrade(
                                    "16",
                                    "Qui",
                                    "Casamento",
                                    "19h30",
                                    false,
                                    List.of(
                                            new CelulaDaGrade(
                                                    "Projeção",
                                                    true,
                                                    List.of(SlotDaGrade.de("Lucas Lima", null, false, false))),
                                            new CelulaDaGrade("Transmissão", false, List.of())))));
            model.addAttribute(
                    "linhaRespondida",
                    new LinhaDeDisponibilidade(
                            500L, "Culto de domingo", "01", "Dom", "01/11 · Dom", "18h00", Resposta.PODE, null, null));
            model.addAttribute(
                    "linhaComAvisos",
                    new LinhaDeDisponibilidade(
                            501L,
                            "Culto de domingo",
                            "08",
                            "Dom",
                            "08/11 · Dom",
                            "19h00",
                            null,
                            "Paula Ribeiro",
                            "O horário mudou (era 18h00). Toque de novo para confirmar."));
            model.addAttribute(
                    "itens",
                    List.of(
                            new ItemDeNavegacao("Minhas escalas", "Escalas", "calendar-check", "/", true),
                            new ItemDeNavegacao(
                                    "Disponibilidade", "Disponib.", "calendar", "/disponibilidade", false)));
            model.addAttribute(
                    "ministerios",
                    List.of(
                            new ItemDeNavegacao("Mídia", "Mídia", "monitor", "/ministerios/1", true),
                            new ItemDeNavegacao("Louvor", "Louvor", "users", "/ministerios/2", false)));
            return "teste/fragmentos";
        }
    }
}
