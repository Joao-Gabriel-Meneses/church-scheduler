package br.igreja.escala.compartilhado.repository;

import br.igreja.escala.compartilhado.domain.Auditoria;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditoriaRepository extends JpaRepository<Auditoria, Long> {

    List<Auditoria> findByMinisterioIdOrderByCriadoEmDesc(Long ministerioId);
}
