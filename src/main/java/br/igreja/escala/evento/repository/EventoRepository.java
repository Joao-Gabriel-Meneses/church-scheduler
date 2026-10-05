package br.igreja.escala.evento.repository;

import br.igreja.escala.evento.domain.Evento;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventoRepository extends JpaRepository<Evento, Long> {

    List<Evento> findByPeriodoIdOrderByDataAscHorarioAsc(Long periodoId);

    /**
     * Já traz o modelo e as funções exigidas: a página do evento mostra os dois fora da transação (open-in-view
     * desligado).
     */
    @EntityGraph(attributePaths = {"modelo", "funcoesExigidas"})
    Optional<Evento> findByIdAndMinisterioId(Long id, Long ministerioId);

    boolean existsByModeloIdAndData(Long modeloId, LocalDate data);

    /** Os eventos do período, cancelados incluídos, já com as funções exigidas (a escala lê fora do serviço). */
    @EntityGraph(attributePaths = "funcoesExigidas")
    List<Evento> findComFuncoesByPeriodoIdOrderByDataAscHorarioAsc(Long periodoId);

    /** Eventos não cancelados dos outros ministérios entre as datas: a sobreposição entre ministérios. */
    @EntityGraph(attributePaths = "funcoesExigidas")
    List<Evento> findByMinisterioIdNotAndCanceladoFalseAndDataBetween(Long ministerioId, LocalDate de, LocalDate ate);

    /** Os eventos, com as funções exigidas e o período (o estado da escala): "Minhas escalas". */
    @EntityGraph(attributePaths = {"funcoesExigidas", "periodo"})
    List<Evento> findComFuncoesEPeriodoByIdIn(Collection<Long> ids);
}
