package br.igreja.escala.ministerio.repository;

import br.igreja.escala.ministerio.domain.Nivel;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NivelRepository extends JpaRepository<Nivel, Long> {

    List<Nivel> findByMinisterioIdOrderByOrdemAsc(Long ministerioId);

    Optional<Nivel> findByIdAndMinisterioId(Long id, Long ministerioId);

    boolean existsByMinisterioIdAndNomeIgnoreCase(Long ministerioId, String nome);

    boolean existsByMinisterioIdAndNomeIgnoreCaseAndIdNot(Long ministerioId, String nome, Long id);

    boolean existsByMinisterioIdAndOrdem(Long ministerioId, int ordem);

    boolean existsByMinisterioIdAndOrdemAndIdNot(Long ministerioId, int ordem, Long id);
}
