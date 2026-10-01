package br.igreja.escala.evento.repository;

import br.igreja.escala.evento.domain.ModeloEvento;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ModeloEventoRepository extends JpaRepository<ModeloEvento, Long> {

    List<ModeloEvento> findByMinisterioIdOrderByDiaDaSemanaAscHorarioAsc(Long ministerioId);

    List<ModeloEvento> findByMinisterioIdAndAtivoTrue(Long ministerioId);

    Optional<ModeloEvento> findByIdAndMinisterioId(Long id, Long ministerioId);
}
