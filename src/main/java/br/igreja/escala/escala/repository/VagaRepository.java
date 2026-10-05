package br.igreja.escala.escala.repository;

import br.igreja.escala.escala.domain.Vaga;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VagaRepository extends JpaRepository<Vaga, Long> {

    List<Vaga> findByEventoIdIn(Collection<Long> eventoIds);

    /** As vagas da pessoa, de todos os ministérios: "Minhas escalas". */
    List<Vaga> findByUsuarioId(Long usuarioId);

    /** As vagas preenchidas desses eventos, de outros ministérios: a sobreposição entre ministérios. */
    List<Vaga> findByEventoIdInAndUsuarioIdIsNotNull(Collection<Long> eventoIds);
}
