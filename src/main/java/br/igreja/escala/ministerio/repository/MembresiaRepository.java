package br.igreja.escala.ministerio.repository;

import br.igreja.escala.ministerio.domain.Membresia;
import br.igreja.escala.ministerio.domain.Ministerio;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface MembresiaRepository extends JpaRepository<Membresia, Long> {

    Optional<Membresia> findByUsuarioIdAndMinisterioId(Long usuarioId, Long ministerioId);

    List<Membresia> findByMinisterioId(Long ministerioId);

    List<Membresia> findByUsuarioId(Long usuarioId);

    boolean existsByUsuarioIdAndMinisterioId(Long usuarioId, Long ministerioId);

    boolean existsByUsuarioIdAndMinisterioIdAndGerenteTrue(Long usuarioId, Long ministerioId);

    boolean existsByUsuarioIdAndGerenteTrue(Long usuarioId);

    List<Membresia> findByGerenteTrue();

    @Query("""
            select m.ministerio.id as ministerioId, count(m) as total
              from Membresia m
             group by m.ministerio.id
            """)
    List<MembrosPorMinisterio> contarMembrosPorMinisterio();

    interface MembrosPorMinisterio {

        Long getMinisterioId();

        long getTotal();
    }

    @Query("""
            select m.ministerio from Membresia m
             where m.usuarioId = :usuarioId and m.gerente = true
             order by m.ministerio.nome
            """)
    List<Ministerio> ministeriosGerenciadosPor(Long usuarioId);
}
