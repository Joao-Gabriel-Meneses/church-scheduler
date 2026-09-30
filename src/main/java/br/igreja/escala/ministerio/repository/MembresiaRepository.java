package br.igreja.escala.ministerio.repository;

import br.igreja.escala.ministerio.domain.Membresia;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MembresiaRepository extends JpaRepository<Membresia, Long> {

    Optional<Membresia> findByUsuarioIdAndMinisterioId(Long usuarioId, Long ministerioId);

    List<Membresia> findByMinisterioId(Long ministerioId);

    List<Membresia> findByUsuarioId(Long usuarioId);

    boolean existsByUsuarioIdAndMinisterioId(Long usuarioId, Long ministerioId);

    boolean existsByUsuarioIdAndMinisterioIdAndGerenteTrue(Long usuarioId, Long ministerioId);

    boolean existsByUsuarioIdAndGerenteTrue(Long usuarioId);
}
