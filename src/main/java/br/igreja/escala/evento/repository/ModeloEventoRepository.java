package br.igreja.escala.evento.repository;

import br.igreja.escala.evento.domain.ModeloEvento;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ModeloEventoRepository extends JpaRepository<ModeloEvento, Long> {

    List<ModeloEvento> findByMinisterioIdOrderByDiaDaSemanaAscHorarioAsc(Long ministerioId);

    List<ModeloEvento> findByMinisterioIdAndAtivoTrue(Long ministerioId);

    /** Já traz as funções exigidas: o formulário do modelo as mostra fora da transação (open-in-view desligado). */
    @EntityGraph(attributePaths = "funcoesExigidas")
    Optional<ModeloEvento> findByIdAndMinisterioId(Long id, Long ministerioId);

    Optional<ModeloEvento> findByMinisterioIdAndDiaDaSemanaAndHorario(
            Long ministerioId, DayOfWeek diaDaSemana, LocalTime horario);
}
