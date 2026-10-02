package br.igreja.escala.evento.service;

import br.igreja.escala.compartilhado.NaoEncontradoException;
import br.igreja.escala.compartilhado.RegraVioladaException;
import br.igreja.escala.evento.domain.ModeloEvento;
import br.igreja.escala.evento.repository.ModeloEventoRepository;
import br.igreja.escala.ministerio.domain.Funcao;
import br.igreja.escala.ministerio.service.FuncaoService;
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
    private final FuncaoService funcoes;

    ModeloEventoService(ModeloEventoRepository modelos, MinisterioService ministerios, FuncaoService funcoes) {
        this.modelos = modelos;
        this.ministerios = ministerios;
        this.funcoes = funcoes;
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

    /**
     * @throws RegraVioladaException no campo {@code horario}, se o ministério já tem um modelo nesse dia e horário, ou
     *     no campo {@code funcoes}, se nenhuma função foi marcada
     * @throws NaoEncontradoException se uma função marcada é de outro ministério
     */
    @Transactional
    public ModeloEvento criar(Long ministerioId, DadosDoModelo dados) {
        ministerios.buscar(ministerioId);
        exigirHorarioLivre(ministerioId, null, dados);
        var modelo =
                new ModeloEvento(ministerioId, dados.nome(), dados.diaDaSemana(), dados.horario(), dados.duracao());
        modelo.alterar(dados.nome(), dados.diaDaSemana(), dados.horario(), dados.duracao(), dados.ativo());
        exigirFuncoes(ministerioId, modelo, dados);
        return modelos.save(modelo);
    }

    /**
     * Muda o modelo para os próximos meses; os eventos já criados ficam como estão.
     *
     * @throws RegraVioladaException no campo {@code horario}, se outro modelo do ministério já usa o dia e horário, ou
     *     no campo {@code funcoes}, se nenhuma função foi marcada
     * @throws NaoEncontradoException se uma função marcada é de outro ministério
     */
    @Transactional
    public ModeloEvento alterar(Long ministerioId, Long id, DadosDoModelo dados) {
        var modelo = buscar(ministerioId, id);
        exigirHorarioLivre(ministerioId, id, dados);
        modelo.alterar(dados.nome(), dados.diaDaSemana(), dados.horario(), dados.duracao(), dados.ativo());
        exigirFuncoes(ministerioId, modelo, dados);
        return modelo;
    }

    private void exigirFuncoes(Long ministerioId, ModeloEvento modelo, DadosDoModelo dados) {
        if (dados.funcoes() != null) {
            var doMinisterio =
                    funcoes.listar(ministerioId).stream().map(Funcao::getId).toList();
            modelo.exigirFuncoes(FuncoesEscolhidas.paraGravar(dados.funcoes(), doMinisterio));
        }
    }

    /** Um modelo por dia e horário no ministério; inativo também conta, porque basta reativá-lo. */
    private void exigirHorarioLivre(Long ministerioId, Long id, DadosDoModelo dados) {
        modelos.findByMinisterioIdAndDiaDaSemanaAndHorario(ministerioId, dados.diaDaSemana(), dados.horario())
                .filter(outro -> !outro.getId().equals(id))
                .ifPresent(outro -> {
                    throw new RegraVioladaException(
                            "horario",
                            "O modelo " + outro.getNome() + " já é neste dia e horário."
                                    + (outro.isAtivo() ? "" : " Ele está inativo: reative-o em vez de criar outro."));
                });
    }
}
