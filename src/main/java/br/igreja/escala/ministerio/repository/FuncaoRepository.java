package br.igreja.escala.ministerio.repository;

import br.igreja.escala.ministerio.domain.Funcao;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FuncaoRepository extends JpaRepository<Funcao, Long> {

    List<Funcao> findByMinisterioIdOrderByNomeAsc(Long ministerioId);

    Optional<Funcao> findByIdAndMinisterioId(Long id, Long ministerioId);

    boolean existsByMinisterioIdAndNomeIgnoreCase(Long ministerioId, String nome);

    boolean existsByMinisterioIdAndNomeIgnoreCaseAndIdNot(Long ministerioId, String nome, Long id);
}
