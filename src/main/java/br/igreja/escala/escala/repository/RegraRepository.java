package br.igreja.escala.escala.repository;

import br.igreja.escala.escala.domain.Regra;
import br.igreja.escala.escala.domain.TipoDeRegra;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RegraRepository extends JpaRepository<Regra, Long> {

    List<Regra> findByMinisterioId(Long ministerioId);

    Optional<Regra> findByMinisterioIdAndTipo(Long ministerioId, TipoDeRegra tipo);
}
