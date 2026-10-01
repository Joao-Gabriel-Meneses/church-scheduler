package br.igreja.escala.evento.repository;

import br.igreja.escala.evento.domain.Periodo;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PeriodoRepository extends JpaRepository<Periodo, Long> {

    Optional<Periodo> findByMinisterioIdAndAnoAndMes(Long ministerioId, int ano, int mes);
}
