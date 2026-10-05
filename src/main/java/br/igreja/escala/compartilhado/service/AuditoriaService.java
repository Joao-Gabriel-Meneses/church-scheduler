package br.igreja.escala.compartilhado.service;

import br.igreja.escala.compartilhado.domain.Auditoria;
import br.igreja.escala.compartilhado.domain.RegistroDeAuditoria;
import br.igreja.escala.compartilhado.repository.AuditoriaRepository;
import br.igreja.escala.compartilhado.web.IpDoCliente;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Registra ações de gerentes e admins. Ponto de entrada público para todos os módulos. */
@Service
public class AuditoriaService {

    private final AuditoriaRepository auditorias;

    AuditoriaService(AuditoriaRepository auditorias) {
        this.auditorias = auditorias;
    }

    /**
     * Grava o registro na mesma transação da ação: se a ação for desfeita, o registro também é, e uma ação nunca
     * acontece sem registro. Guarda o IP da requisição em andamento ({@link IpDoCliente}), se houver.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void registrar(RegistroDeAuditoria registro) {
        auditorias.save(new Auditoria(registro, IpDoCliente.atual()));
    }
}
