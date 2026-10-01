package br.igreja.escala.identidade.web;

import static org.assertj.core.api.Assertions.assertThat;

import br.igreja.escala.Pessoas;
import br.igreja.escala.identidade.service.AcessoRevogado;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.session.SessionRegistryImpl;

class SessoesAbertasTest {

    private final SessionRegistryImpl registro = new SessionRegistryImpl();
    private final SessoesAbertas sessoes = new SessoesAbertas(registro);

    @BeforeEach
    void registraAsSessoes() {
        // Dois logins da Ana (celular e computador) e um da Bia; cada login traz um principal novo.
        registro.registerNewSession("ana-celular", Pessoas.membro(1L, "Ana"));
        registro.registerNewSession("ana-computador", Pessoas.membro(1L, "Ana Souza"));
        registro.registerNewSession("bia", Pessoas.membro(2L, "Bia"));
    }

    @Test
    void encerraTodasAsSessoesDaPessoaESoDela() {
        sessoes.encerrarTodas(1L);

        assertThat(expirada("ana-celular")).isTrue();
        assertThat(expirada("ana-computador")).isTrue();
        assertThat(expirada("bia")).isFalse();
    }

    @Test
    void naTrocaDaPropriaSenhaMantemASessaoAtual() {
        sessoes.encerrarOutras(1L, "ana-computador");

        assertThat(expirada("ana-celular")).isTrue();
        assertThat(expirada("ana-computador")).isFalse();
        assertThat(expirada("bia")).isFalse();
    }

    @Test
    void acessoRevogadoEncerraTodas() {
        sessoes.aoRevogarAcesso(new AcessoRevogado(2L));

        assertThat(expirada("bia")).isTrue();
        assertThat(expirada("ana-celular")).isFalse();
    }

    @Test
    void semSessaoNaoFazNada() {
        sessoes.encerrarTodas(99L);

        assertThat(registro.getAllPrincipals()).hasSize(2);
        assertThat(expirada("ana-celular")).isFalse();
    }

    private boolean expirada(String sessao) {
        return registro.getSessionInformation(sessao).isExpired();
    }
}
