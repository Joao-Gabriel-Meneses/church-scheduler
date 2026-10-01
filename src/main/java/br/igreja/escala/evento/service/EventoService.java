package br.igreja.escala.evento.service;

import br.igreja.escala.compartilhado.Datas;
import br.igreja.escala.compartilhado.NaoEncontradoException;
import br.igreja.escala.compartilhado.RegraVioladaException;
import br.igreja.escala.evento.domain.Evento;
import br.igreja.escala.evento.domain.ModeloEvento;
import br.igreja.escala.evento.repository.EventoRepository;
import br.igreja.escala.ministerio.service.MinisterioService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Eventos de cada mês: gerados dos modelos ou avulsos. "Hoje" vem do relógio de São Paulo (RelogioConfig). */
@Service
public class EventoService {

    private final EventoRepository eventos;
    private final PeriodoService periodos;
    private final ModeloEventoService modelos;
    private final MinisterioService ministerios;
    private final Clock relogio;

    EventoService(
            EventoRepository eventos,
            PeriodoService periodos,
            ModeloEventoService modelos,
            MinisterioService ministerios,
            Clock relogio) {
        this.eventos = eventos;
        this.periodos = periodos;
        this.modelos = modelos;
        this.ministerios = ministerios;
        this.relogio = relogio;
    }

    /** O mês que o gerente prepara: o seguinte ao de hoje. */
    public YearMonth proximoMes() {
        return YearMonth.now(relogio).plusMonths(1);
    }

    /** Os eventos do mês, cancelados incluídos, por data e horário. */
    @Transactional(readOnly = true)
    public List<EventoResumo> doMes(Long ministerioId, YearMonth mes) {
        return periodos
                .doMes(ministerioId, mes)
                .map(periodo -> eventos.findByPeriodoIdOrderByDataAscHorarioAsc(periodo.getId()))
                .orElse(List.of())
                .stream()
                .map(EventoResumo::de)
                .toList();
    }

    /**
     * Cria os eventos do mês a partir dos modelos ativos, de hoje em diante. Pode rodar de novo à vontade: não duplica
     * evento que já existe e não recria um cancelado.
     *
     * @return quantos eventos foram criados agora
     * @throws RegraVioladaException se o mês já passou
     */
    @Transactional
    public int gerarDoMes(Long ministerioId, YearMonth mes) {
        ministerios.buscar(ministerioId);
        var hoje = LocalDate.now(relogio);
        if (mes.isBefore(YearMonth.from(hoje))) {
            throw RegraVioladaException.geral(Datas.mesPorExtenso(mes) + " já passou: não dá para criar eventos nele.");
        }
        var periodo = periodos.obterOuCriar(ministerioId, mes);
        int criados = 0;
        for (ModeloEvento modelo : modelos.ativos(ministerioId)) {
            for (LocalDate data : datasDoModelo(modelo, mes).toList()) {
                if (!data.isBefore(hoje) && !eventos.existsByModeloIdAndData(modelo.getId(), data)) {
                    eventos.save(Evento.doModelo(modelo, periodo, data));
                    criados++;
                }
            }
        }
        return criados;
    }

    /**
     * @throws RegraVioladaException no campo {@code data}, se a data já passou
     */
    @Transactional
    public Evento criarAvulso(Long ministerioId, DadosDoEvento dados) {
        ministerios.buscar(ministerioId);
        exigirHojeOuDepois(dados.data());
        var periodo = periodos.obterOuCriar(ministerioId, YearMonth.from(dados.data()));
        return eventos.save(Evento.avulso(periodo, dados.nome(), dados.data(), dados.horario()));
    }

    /**
     * Muda nome e horário; num avulso, também a data. Não muda o modelo de onde o evento veio.
     *
     * @throws RegraVioladaException no campo {@code data}, se tentar mudar a data de um evento do modelo ou pôr um
     *     avulso numa data que já passou
     */
    @Transactional
    public Evento alterar(Long ministerioId, Long eventoId, DadosDoEvento dados) {
        var evento = buscar(ministerioId, eventoId);
        if (!dados.data().equals(evento.getData())) {
            if (!evento.isAvulso()) {
                throw new RegraVioladaException(
                        "data",
                        "A data de um evento do modelo não muda. Cancele este e crie um evento avulso na data nova.");
            }
            exigirHojeOuDepois(dados.data());
            evento.mudarData(dados.data(), periodos.obterOuCriar(ministerioId, YearMonth.from(dados.data())));
        }
        evento.alterar(dados.nome(), dados.horario());
        return evento;
    }

    @Transactional
    public Evento cancelar(Long ministerioId, Long eventoId) {
        var evento = buscar(ministerioId, eventoId);
        evento.cancelar();
        return evento;
    }

    @Transactional
    public Evento reativar(Long ministerioId, Long eventoId) {
        var evento = buscar(ministerioId, eventoId);
        evento.reativar();
        return evento;
    }

    /**
     * @throws NaoEncontradoException se o evento não existe ou é de outro ministério
     */
    @Transactional(readOnly = true)
    public Evento buscar(Long ministerioId, Long eventoId) {
        return eventos.findByIdAndMinisterioId(eventoId, ministerioId)
                .orElseThrow(() -> new NaoEncontradoException("Evento " + eventoId + " no ministério " + ministerioId));
    }

    private void exigirHojeOuDepois(LocalDate data) {
        if (data.isBefore(LocalDate.now(relogio))) {
            throw new RegraVioladaException("data", "Escolha hoje ou uma data futura.");
        }
    }

    private static Stream<LocalDate> datasDoModelo(ModeloEvento modelo, YearMonth mes) {
        var primeira = mes.atDay(1).with(TemporalAdjusters.nextOrSame(modelo.getDiaDaSemana()));
        return Stream.iterate(primeira, data -> YearMonth.from(data).equals(mes), data -> data.plusWeeks(1));
    }
}
