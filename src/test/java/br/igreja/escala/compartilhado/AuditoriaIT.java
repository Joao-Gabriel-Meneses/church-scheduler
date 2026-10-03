package br.igreja.escala.compartilhado;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.igreja.escala.TesteDeIntegracao;
import br.igreja.escala.compartilhado.domain.AcaoAuditada;
import br.igreja.escala.compartilhado.domain.RegistroDeAuditoria;
import br.igreja.escala.compartilhado.repository.AuditoriaRepository;
import br.igreja.escala.compartilhado.service.AuditoriaService;
import br.igreja.escala.identidade.domain.Usuario;
import br.igreja.escala.identidade.repository.UsuarioRepository;
import br.igreja.escala.ministerio.domain.CorDoMinisterio;
import br.igreja.escala.ministerio.domain.Icone;
import br.igreja.escala.ministerio.domain.Ministerio;
import br.igreja.escala.ministerio.repository.MinisterioRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@TesteDeIntegracao
class AuditoriaIT {

    @Autowired
    AuditoriaService auditoria;

    @Autowired
    AuditoriaRepository auditorias;

    @Autowired
    UsuarioRepository usuarios;

    @Autowired
    MinisterioRepository ministerios;

    @Autowired
    EntityManager entityManager;

    @Test
    @Transactional
    void gravaNoOracleComDataDeCriacao() {
        var gerente = usuarios.save(Usuario.membro("Gerente", "gerente.auditoria@teste.local", "{noop}x"));
        var ana = usuarios.save(Usuario.membro("Ana", "ana.auditoria@teste.local", "{noop}x"));
        var midia = ministerios.save(new Ministerio("Mídia Auditoria", CorDoMinisterio.MINT, Icone.MONITOR));

        var requisicao = new MockHttpServletRequest();
        requisicao.addHeader("CF-Connecting-IP", "2804:14c:65a1:4000::1f");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(requisicao));
        try {
            auditoria.registrar(new RegistroDeAuditoria(
                    AcaoAuditada.REDEFINIR_SENHA,
                    gerente.getId(),
                    midia.getId(),
                    ana.getId(),
                    "Senha de Ana redefinida"));
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }
        entityManager.flush();
        entityManager.clear();

        var registros = auditorias.findByMinisterioIdOrderByCriadoEmDesc(midia.getId());
        assertThat(registros).singleElement().satisfies(registro -> {
            assertThat(registro.getAcao()).isEqualTo(AcaoAuditada.REDEFINIR_SENHA);
            assertThat(registro.getAlvoUsuarioId()).isEqualTo(ana.getId());
            assertThat(registro.getIp()).isEqualTo("2804:14c:65a1:4000::1f");
            assertThat(registro.getCriadoEm()).isNotNull();
        });
    }

    /** Sem transação não há como garantir que a ação e o registro andem juntos. */
    @Test
    @Transactional(propagation = Propagation.NEVER)
    void recusaRegistrarForaDeUmaTransacao() {
        assertThatThrownBy(() ->
                        auditoria.registrar(new RegistroDeAuditoria(AcaoAuditada.REMOVER_MEMBRO, 1L, null, null, "x")))
                .isInstanceOf(IllegalTransactionStateException.class);
    }
}
