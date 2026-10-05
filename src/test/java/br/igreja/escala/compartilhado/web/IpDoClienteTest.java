package br.igreja.escala.compartilhado.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

class IpDoClienteTest {

    private final MockHttpServletRequest requisicao = new MockHttpServletRequest();

    IpDoClienteTest() {
        requisicao.setRemoteAddr("172.18.0.3");
    }

    @AfterEach
    void limparRequisicaoDaThread() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void usaOIpDoUsuarioQueOCloudflareManda() {
        requisicao.addHeader("CF-Connecting-IP", "189.40.12.7");

        assertThat(IpDoCliente.de(requisicao)).isEqualTo("189.40.12.7");
    }

    @Test
    void aceitaIpv6() {
        requisicao.addHeader("CF-Connecting-IP", " 2804:14c:65a1:4000::1f ");

        assertThat(IpDoCliente.de(requisicao)).isEqualTo("2804:14c:65a1:4000::1f");
    }

    @Test
    void semOCabecalhoUsaOIpDaRequisicao() {
        assertThat(IpDoCliente.de(requisicao)).isEqualTo("172.18.0.3");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {"", "   ", "189.40.12.7, 10.0.0.1", "<script>", "1111:2222:3333:4444:5555:6666:7777:8888:9999:0"
            })
    void cabecalhoVazioOuQueNaoEIpCaiNoIpDaRequisicao(String valor) {
        requisicao.addHeader("CF-Connecting-IP", valor);

        assertThat(IpDoCliente.de(requisicao)).isEqualTo("172.18.0.3");
    }

    @Test
    void atualLeARequisicaoDaThread() {
        requisicao.addHeader("CF-Connecting-IP", "189.40.12.7");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(requisicao));

        assertThat(IpDoCliente.atual()).isEqualTo("189.40.12.7");
    }

    @Test
    void foraDeUmaRequisicaoNaoHaIp() {
        assertThat(IpDoCliente.atual()).isNull();
    }
}
