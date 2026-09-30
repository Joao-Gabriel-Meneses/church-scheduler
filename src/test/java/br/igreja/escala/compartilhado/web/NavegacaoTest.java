package br.igreja.escala.compartilhado.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import br.igreja.escala.compartilhado.web.Navegacao.Entrada;
import br.igreja.escala.identidade.domain.Perfil;
import br.igreja.escala.visual.IconesTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;

class NavegacaoTest {

    private final Navegacao navegacao = new Navegacao(List.of(
            new Entrada("Minhas escalas", "Escalas", "calendar-check", "/", Perfil.MEMBRO),
            new Entrada("Membros", "Membros", "users", "/membros", Perfil.MEMBRO),
            new Entrada("Ministérios", "Ministérios", "church", "/ministerios", Perfil.ADMIN)));

    @Test
    void membroNaoVeItensDeAdmin() {
        var itens = navegacao.itens(AuthorityUtils.createAuthorityList("ROLE_MEMBRO"), "/");

        assertThat(itens).extracting(ItemDeNavegacao::rotulo).containsExactly("Minhas escalas", "Membros");
    }

    @Test
    void adminVeTudoNaOrdemDoCatalogo() {
        var itens = navegacao.itens(AuthorityUtils.createAuthorityList("ROLE_MEMBRO", "ROLE_ADMIN"), "/");

        assertThat(itens)
                .extracting(ItemDeNavegacao::rotulo)
                .containsExactly("Minhas escalas", "Membros", "Ministérios");
    }

    @Test
    void marcaSoOItemDaSecaoAtualInclusiveEmSubpaginas() {
        var itens = navegacao.itens(AuthorityUtils.createAuthorityList("ROLE_MEMBRO"), "/membros/3");

        assertThat(itens).extracting(ItemDeNavegacao::atual).containsExactly(false, true);
    }

    @Test
    void inicioSoEAtualNaRaizESecaoNaoCasaPorPrefixoDePalavra() {
        var itens = navegacao.itens(AuthorityUtils.createAuthorityList("ROLE_MEMBRO"), "/membrosx");

        assertThat(itens).extracting(ItemDeNavegacao::atual).containsExactly(false, false);
        assertThat(navegacao.itens(AuthorityUtils.createAuthorityList("ROLE_MEMBRO"), "/"))
                .extracting(ItemDeNavegacao::atual)
                .containsExactly(true, false);
    }

    @Test
    void navegacaoPrincipalUsaSoIconesDoSprite() {
        var todos = AuthorityUtils.createAuthorityList("ROLE_MEMBRO", "ROLE_ADMIN");

        assertThat(Navegacao.PRINCIPAL.itens(todos, "/"))
                .isNotEmpty()
                .extracting(ItemDeNavegacao::icone)
                .isSubsetOf(IconesTest.disponiveis());
    }

    @Test
    void adviceMontaANavegacaoDoUsuarioLogadoSemOContexto() {
        var requisicao = new MockHttpServletRequest("GET", "/app/");
        requisicao.setContextPath("/app");
        var logado = UsernamePasswordAuthenticationToken.authenticated(
                "ana", null, AuthorityUtils.createAuthorityList("ROLE_MEMBRO"));

        assertThat(new NavegacaoAdvice().navegacao(logado, requisicao))
                .extracting(ItemDeNavegacao::rotulo, ItemDeNavegacao::atual)
                .first()
                .isEqualTo(tuple("Minhas escalas", true));
    }

    @Test
    void adviceNaoMostraNavegacaoSemLogin() {
        var requisicao = new MockHttpServletRequest("GET", "/login");
        var anonimo = new AnonymousAuthenticationToken(
                "chave", "anonimo", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));

        assertThat(new NavegacaoAdvice().navegacao(null, requisicao)).isEmpty();
        assertThat(new NavegacaoAdvice().navegacao(anonimo, requisicao)).isEmpty();
    }
}
