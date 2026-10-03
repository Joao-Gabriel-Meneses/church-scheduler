package br.igreja.escala.compartilhado.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import br.igreja.escala.compartilhado.web.Navegacao.Area;
import br.igreja.escala.compartilhado.web.Navegacao.Entrada;
import br.igreja.escala.identidade.domain.Perfil;
import br.igreja.escala.visual.IconesTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.ui.ExtendedModelMap;

class NavegacaoTest {

    private static final List<MinisterioNaNavegacao> NENHUM = List.of();
    private static final List<MinisterioNaNavegacao> MIDIA_E_LOUVOR = List.of(
            new MinisterioNaNavegacao(1L, "Mídia", "monitor"), new MinisterioNaNavegacao(2L, "Louvor", "music"));

    private final Navegacao navegacao = new Navegacao(List.of(
            new Entrada(Area.MEMBRO, "Minhas escalas", "Escalas", "calendar-check", "/", Perfil.MEMBRO),
            new Entrada(
                    Area.MEMBRO,
                    "Gerenciar",
                    "Gerenciar",
                    "layout-dashboard",
                    "/ministerios/{ministerio}/eventos",
                    Perfil.MEMBRO),
            new Entrada(Area.MEMBRO, "Ministérios", "Ministérios", "church", "/admin/ministerios", Perfil.ADMIN),
            new Entrada(
                    Area.MINISTERIO,
                    "Eventos",
                    "Eventos",
                    "calendar",
                    "/ministerios/{ministerio}/eventos",
                    Perfil.MEMBRO),
            new Entrada(
                    Area.MINISTERIO,
                    "Membros",
                    "Membros",
                    "users",
                    "/ministerios/{ministerio}/membros",
                    Perfil.MEMBRO)));

    @Test
    void membroQueNaoGerenciaNadaVeSoAAreaDele() {
        var itens = navegacao.itens(membro(), "/", NENHUM);

        assertThat(itens).extracting(ItemDeNavegacao::rotulo).containsExactly("Minhas escalas");
    }

    @Test
    void quemGerenciaGanhaOAtalhoParaOPrimeiroMinisterio() {
        var itens = navegacao.itens(membro(), "/", MIDIA_E_LOUVOR);

        assertThat(itens)
                .extracting(ItemDeNavegacao::rotulo, ItemDeNavegacao::url)
                .containsExactly(tuple("Minhas escalas", "/"), tuple("Gerenciar", "/ministerios/1/eventos"));
    }

    @Test
    void adminGanhaOsMinisteriosNaOrdemDoCatalogo() {
        var itens = navegacao.itens(admin(), "/admin/ministerios/novo", MIDIA_E_LOUVOR);

        assertThat(itens)
                .extracting(ItemDeNavegacao::rotulo, ItemDeNavegacao::atual)
                .containsExactly(tuple("Minhas escalas", false), tuple("Gerenciar", false), tuple("Ministérios", true));
    }

    @Test
    void dentroDeUmMinisterioAsPaginasSaoDoGerenteDaqueleMinisterio() {
        var itens = navegacao.itens(membro(), "/ministerios/2/membros/7", MIDIA_E_LOUVOR);

        assertThat(itens)
                .extracting(ItemDeNavegacao::rotulo, ItemDeNavegacao::url, ItemDeNavegacao::atual)
                .containsExactly(
                        tuple("Eventos", "/ministerios/2/eventos", false),
                        tuple("Membros", "/ministerios/2/membros", true));
    }

    @Test
    void inicioSoEAtualNaRaizESecaoNaoCasaPorPrefixoDePalavra() {
        assertThat(navegacao.itens(membro(), "/ministerios/1/eventosx", NENHUM))
                .extracting(ItemDeNavegacao::atual)
                .containsExactly(false, false);
        assertThat(navegacao.itens(membro(), "/", NENHUM))
                .extracting(ItemDeNavegacao::atual)
                .containsExactly(true);
    }

    @Test
    void sideRailMostraOsMinisteriosNaMesmaSecaoSoDentroDeUmMinisterio() {
        assertThat(Navegacao.ministerios("/ministerios/1/membros/7", MIDIA_E_LOUVOR))
                .extracting(ItemDeNavegacao::rotulo, ItemDeNavegacao::url, ItemDeNavegacao::atual)
                .containsExactly(
                        tuple("Mídia", "/ministerios/1/membros", true),
                        tuple("Louvor", "/ministerios/2/membros", false));
        assertThat(Navegacao.ministerios("/ministerios/2", MIDIA_E_LOUVOR))
                .extracting(ItemDeNavegacao::url)
                .containsExactly("/ministerios/1/escalas", "/ministerios/2/escalas");
        assertThat(Navegacao.ministerios("/", MIDIA_E_LOUVOR)).isEmpty();
        assertThat(Navegacao.ministerios("/admin/ministerios", MIDIA_E_LOUVOR)).isEmpty();
    }

    @Test
    void navegacaoPrincipalUsaSoIconesDoSprite() {
        var todos = AuthorityUtils.createAuthorityList("ROLE_MEMBRO", "ROLE_ADMIN");

        assertThat(Navegacao.PRINCIPAL.itens(todos, "/", MIDIA_E_LOUVOR))
                .extracting(ItemDeNavegacao::rotulo)
                .containsExactly("Minhas escalas", "Disponibilidade", "Gerenciar", "Ministérios");
        assertThat(Navegacao.PRINCIPAL.itens(todos, "/", MIDIA_E_LOUVOR))
                .extracting(ItemDeNavegacao::icone)
                .isSubsetOf(IconesTest.disponiveis());
        assertThat(Navegacao.PRINCIPAL.itens(todos, "/ministerios/1/funcoes", MIDIA_E_LOUVOR))
                .extracting(ItemDeNavegacao::rotulo)
                .containsExactly(
                        "Escalas",
                        "Eventos",
                        "Disponibilidade",
                        "Membros",
                        "Funções",
                        "Regras",
                        "Ministérios",
                        "Minhas escalas");
        assertThat(Navegacao.PRINCIPAL.itens(todos, "/ministerios/1/funcoes", MIDIA_E_LOUVOR))
                .extracting(ItemDeNavegacao::icone)
                .isSubsetOf(IconesTest.disponiveis());
    }

    @Test
    void adviceMontaANavegacaoEARailDoUsuarioLogadoSemOContexto() {
        var requisicao = new MockHttpServletRequest("GET", "/app/ministerios/2/membros");
        requisicao.setContextPath("/app");
        var logado = logado("ROLE_MEMBRO");
        var model = new ExtendedModelMap();

        advice(MIDIA_E_LOUVOR, logado).navegacao(logado, requisicao, model);

        assertThat(lista(model, "navegacao"))
                .extracting(ItemDeNavegacao::rotulo, ItemDeNavegacao::atual)
                .contains(tuple("Membros", true));
        assertThat(lista(model, "ministerios"))
                .extracting(ItemDeNavegacao::rotulo, ItemDeNavegacao::atual)
                .containsExactly(tuple("Mídia", false), tuple("Louvor", true));
    }

    @Test
    void adviceSemOModuloDeMinisteriosMontaSoAAreaDoMembro() {
        var model = new ExtendedModelMap();
        var logado = logado("ROLE_MEMBRO");

        new NavegacaoAdvice(semFonte()).navegacao(logado, new MockHttpServletRequest("GET", "/"), model);

        assertThat(lista(model, "navegacao"))
                .extracting(ItemDeNavegacao::rotulo)
                .containsExactly("Minhas escalas", "Disponibilidade");
        assertThat(lista(model, "ministerios")).isEmpty();
    }

    @Test
    void adviceNaoMostraNavegacaoSemLogin() {
        var requisicao = new MockHttpServletRequest("GET", "/login");
        var anonimo = new AnonymousAuthenticationToken(
                "chave", "anonimo", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));
        var semLogin = new ExtendedModelMap();
        var comAnonimo = new ExtendedModelMap();

        new NavegacaoAdvice(semFonte()).navegacao(null, requisicao, semLogin);
        new NavegacaoAdvice(semFonte()).navegacao(anonimo, requisicao, comAnonimo);

        assertThat(lista(semLogin, "navegacao")).isEmpty();
        assertThat(lista(comAnonimo, "navegacao")).isEmpty();
    }

    private static List<GrantedAuthority> membro() {
        return AuthorityUtils.createAuthorityList("ROLE_MEMBRO");
    }

    private static List<GrantedAuthority> admin() {
        return AuthorityUtils.createAuthorityList("ROLE_MEMBRO", "ROLE_ADMIN");
    }

    private static Authentication logado(String... autoridades) {
        return UsernamePasswordAuthenticationToken.authenticated(
                "ana", null, AuthorityUtils.createAuthorityList(autoridades));
    }

    @SuppressWarnings("unchecked")
    private static ObjectProvider<MinisteriosGerenciados> fonte(MinisteriosGerenciados gerenciados) {
        ObjectProvider<MinisteriosGerenciados> provedor = mock(ObjectProvider.class);
        when(provedor.getIfAvailable()).thenReturn(gerenciados);
        return provedor;
    }

    private static ObjectProvider<MinisteriosGerenciados> semFonte() {
        return fonte(null);
    }

    private static NavegacaoAdvice advice(List<MinisterioNaNavegacao> ministerios, Authentication logado) {
        MinisteriosGerenciados gerenciados = mock(MinisteriosGerenciados.class);
        when(gerenciados.de(logado)).thenReturn(ministerios);
        return new NavegacaoAdvice(fonte(gerenciados));
    }

    @SuppressWarnings("unchecked")
    private static List<ItemDeNavegacao> lista(ExtendedModelMap model, String nome) {
        return (List<ItemDeNavegacao>) model.getAttribute(nome);
    }
}
