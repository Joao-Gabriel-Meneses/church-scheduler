package br.igreja.escala.compartilhado.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import br.igreja.escala.compartilhado.domain.AcaoAuditada;
import br.igreja.escala.compartilhado.domain.Auditoria;
import br.igreja.escala.compartilhado.domain.RegistroDeAuditoria;
import br.igreja.escala.compartilhado.repository.AuditoriaRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

class AuditoriaServiceTest {

    private final AuditoriaRepository repositorio = mock(AuditoriaRepository.class);
    private final AuditoriaService auditoria = new AuditoriaService(repositorio);

    @AfterEach
    void limparRequisicaoDaThread() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void gravaQuemFezOndeSobreQuemEOQue() {
        auditoria.registrar(new RegistroDeAuditoria(
                AcaoAuditada.NOMEAR_GERENTE, 1L, 2L, 3L, "  Ana Souza virou gerente da Mídia "));

        var gravada = ArgumentCaptor.forClass(Auditoria.class);
        verify(repositorio).save(gravada.capture());
        assertThat(gravada.getValue())
                .extracting(
                        Auditoria::getAcao,
                        Auditoria::getAutorId,
                        Auditoria::getMinisterioId,
                        Auditoria::getAlvoUsuarioId,
                        Auditoria::getDescricao)
                .containsExactly(AcaoAuditada.NOMEAR_GERENTE, 1L, 2L, 3L, "Ana Souza virou gerente da Mídia");
    }

    @Test
    void guardaOIpDoUsuarioDaRequisicaoEmAndamento() {
        var requisicao = new MockHttpServletRequest();
        requisicao.setRemoteAddr("172.18.0.3");
        requisicao.addHeader("CF-Connecting-IP", "189.40.12.7");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(requisicao));

        auditoria.registrar(new RegistroDeAuditoria(AcaoAuditada.REDEFINIR_SENHA, 1L, null, 3L, "x"));

        var gravada = ArgumentCaptor.forClass(Auditoria.class);
        verify(repositorio).save(gravada.capture());
        assertThat(gravada.getValue().getIp()).isEqualTo("189.40.12.7");
    }

    /** A gravação da geração roda numa fila, sem requisição. */
    @Test
    void foraDeUmaRequisicaoGravaSemIp() {
        auditoria.registrar(new RegistroDeAuditoria(AcaoAuditada.GERAR_ESCALA, 1L, 2L, null, "x"));

        var gravada = ArgumentCaptor.forClass(Auditoria.class);
        verify(repositorio).save(gravada.capture());
        assertThat(gravada.getValue().getIp()).isNull();
    }

    @Test
    void ministerioEAlvoSaoOpcionais() {
        var registro = new Auditoria(new RegistroDeAuditoria(AcaoAuditada.REDEFINIR_SENHA, 1L, null, null, "x"));

        assertThat(registro.getMinisterioId()).isNull();
        assertThat(registro.getAlvoUsuarioId()).isNull();
    }

    @Test
    void exigeAcaoAutorEDescricao() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Auditoria(new RegistroDeAuditoria(null, 1L, null, null, "x")));
        assertThatIllegalArgumentException()
                .isThrownBy(() ->
                        new Auditoria(new RegistroDeAuditoria(AcaoAuditada.REMOVER_MEMBRO, null, null, null, "x")));
        assertThatIllegalArgumentException()
                .isThrownBy(
                        () -> new Auditoria(new RegistroDeAuditoria(AcaoAuditada.REMOVER_MEMBRO, 1L, null, null, " ")));
    }
}
