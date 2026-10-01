package br.igreja.escala.evento.service;

import br.igreja.escala.compartilhado.NaoEncontradoException;
import br.igreja.escala.evento.domain.ModeloEvento;
import br.igreja.escala.evento.repository.ModeloEventoRepository;
import br.igreja.escala.ministerio.service.MinisterioService;
import java.time.DayOfWeek;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Modelos de evento de um ministério. Não se excluem (os eventos apontam para eles): um modelo que não vale mais fica
 * inativo e deixa de gerar eventos.
 */
@Service
public class ModeloEventoService {

    /** A semana da igreja começa no domingo. */
    static final Comparator<ModeloEvento> ORDEM_DA_SEMANA = Comparator.comparing(
                    (ModeloEvento modelo) -> modelo.getDiaDaSemana() == DayOfWeek.SUNDAY
                            ? 0
                            : modelo.getDiaDaSemana().getValue())
            .thenComparing(ModeloEvento::getHorario);

    private final ModeloEventoRepository modelos;
    private final MinisterioService ministerios;

    ModeloEventoService(ModeloEventoRepository modelos, MinisterioService ministerios) {
        this.modelos = modelos;
        this.ministerios = ministerios;
    }

    /** Todos os modelos do ministério, do domingo ao sábado. */
    @Transactional(readOnly = true)
    public List<ModeloResumo> resumos(Long ministerioId) {
        return modelos.findByMinisterioIdOrderByDiaDaSemanaAscHorarioAsc(ministerioId).stream()
                .sorted(ORDEM_DA_SEMANA)
                .map(ModeloResumo::de)
                .toList();
    }

    /** Os modelos que geram eventos. */
    @Transactional(readOnly = true)
    public List<ModeloEvento> ativos(Long ministerioId) {
        return modelos.findByMinisterioIdAndAtivoTrue(ministerioId);
    }

    /**
     * @throws NaoEncontradoException se o modelo não existe ou é de outro ministério
     */
    @Transactional(readOnly = true)
    public ModeloEvento buscar(Long ministerioId, Long id) {
        return modelos.findByIdAndMinisterioId(id, ministerioId)
                .orElseThrow(() -> new NaoEncontradoException("Modelo " + id + " no ministério " + ministerioId));
    }

    @Transactional
    public ModeloEvento criar(Long ministerioId, DadosDoModelo dados) {
        ministerios.buscar(ministerioId);
        var modelo = new ModeloEvento(ministerioId, dados.nome(), dados.diaDaSemana(), dados.horario());
        modelo.alterar(dados.nome(), dados.diaDaSemana(), dados.horario(), dados.ativo());
        return modelos.save(modelo);
    }

    /** Muda o modelo para os próximos meses; os eventos já criados ficam como estão. */
    @Transactional
    public ModeloEvento alterar(Long ministerioId, Long id, DadosDoModelo dados) {
        var modelo = buscar(ministerioId, id);
        modelo.alterar(dados.nome(), dados.diaDaSemana(), dados.horario(), dados.ativo());
        return modelo;
    }
}
