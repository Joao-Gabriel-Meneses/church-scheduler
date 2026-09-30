package br.igreja.escala.ministerio.repository;

import br.igreja.escala.ministerio.domain.Ministerio;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MinisterioRepository extends JpaRepository<Ministerio, Long> {

    List<Ministerio> findAllByOrderByNomeAsc();

    boolean existsByNomeIgnoreCase(String nome);

    boolean existsByNomeIgnoreCaseAndIdNot(String nome, Long id);
}
