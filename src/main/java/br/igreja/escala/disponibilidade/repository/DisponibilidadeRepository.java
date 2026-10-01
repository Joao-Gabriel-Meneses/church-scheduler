package br.igreja.escala.disponibilidade.repository;

import br.igreja.escala.disponibilidade.domain.Disponibilidade;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DisponibilidadeRepository extends JpaRepository<Disponibilidade, Long> {

    Optional<Disponibilidade> findByUsuarioIdAndEventoId(Long usuarioId, Long eventoId);

    /** As respostas da pessoa aos eventos de um mês (bem menos que os 1000 itens do IN do Oracle). */
    List<Disponibilidade> findByUsuarioIdAndEventoIdIn(Long usuarioId, Collection<Long> eventoIds);

    List<Disponibilidade> findByEventoIdIn(Collection<Long> eventoIds);
}
