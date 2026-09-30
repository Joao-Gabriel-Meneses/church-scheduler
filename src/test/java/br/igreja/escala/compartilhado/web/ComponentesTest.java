package br.igreja.escala.compartilhado.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.igreja.escala.TesteDeController;
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
        assertThat(secao("rail"))
                .contains("<nav class=\"rt-rail\" aria-label=\"Ministérios\">")
                .contains("aria-label=\"Mídia\" title=\"Mídia\" aria-current=\"page\"")
                .contains("aria-label=\"Louvor\"");
        assertThat(secao("rail-unica")).doesNotContain("<nav");
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
    void toolbarMinimaSoTemPeriodoETravaAbertaNoSheet() {
        assertThat(secao("toolbar-minima"))
                .contains("class=\"rt-badge rt-toolbar__so-sheet\"", "Disponibilidade aberta")
                .doesNotContain("rt-toolbar__regras", "rt-btn", "rt-badge--locked");
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
