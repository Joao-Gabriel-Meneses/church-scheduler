package br.igreja.escala.compartilhado.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import br.igreja.escala.compartilhado.domain.AcaoAuditada;
import br.igreja.escala.compartilhado.domain.Auditoria;
import br.igreja.escala.compartilhado.domain.RegistroDeAuditoria;
import br.igreja.escala.compartilhado.repository.AuditoriaRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AuditoriaServiceTest {

    private final AuditoriaRepository repositorio = mock(AuditoriaRepository.class);
    private final AuditoriaService auditoria = new AuditoriaService(repositorio);

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
