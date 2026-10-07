package br.igreja.escala.escala.service;

import br.igreja.escala.compartilhado.NaoEncontradoException;
import br.igreja.escala.compartilhado.web.EscalasNoInicio;
import br.igreja.escala.compartilhado.web.MinhasEscalas;
import br.igreja.escala.escala.domain.Vaga;
import br.igreja.escala.escala.repository.VagaRepository;
import br.igreja.escala.evento.domain.Evento;
import br.igreja.escala.evento.service.EventoService;
import br.igreja.escala.evento.service.PeriodoService;
import br.igreja.escala.identidade.domain.UsuarioAutenticado;
import br.igreja.escala.ministerio.domain.Funcao;
import br.igreja.escala.ministerio.domain.Ministerio;
import br.igreja.escala.ministerio.service.FuncaoService;
import br.igreja.escala.ministerio.service.MembroService;
import br.igreja.escala.ministerio.service.MinisterioService;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A escala vista pelo membro, só a publicada: as próprias escalas ("Minhas escalas", no início, com o que ainda dá para
 * desistir) e a grade do mês de cada ministério de que ele é membro. Rascunho nunca aparece, nem por URL direta.
 */
@Service
public class EscalaDoMembro implements EscalasNoInicio {

    /** Quantos dias para trás entram nas escalas passadas (recolhidas no início). */
    static final int DIAS_DAS_PASSADAS = 60;

    private final VagaRepository vagas;
    private final EventoService eventos;
    private final FuncaoService funcoes;
    private final MinisterioService ministerios;
    private final MembroService membros;
    private final PeriodoService periodos;
    private final LeituraDoPeriodo leitura;
    private final Clock relogio;

    EscalaDoMembro(
            VagaRepository vagas,
            EventoService eventos,
            FuncaoService funcoes,
            MinisterioService ministerios,
            MembroService membros,
            PeriodoService periodos,
            LeituraDoPeriodo leitura,
            Clock relogio) {
        this.vagas = vagas;
        this.eventos = eventos;
        this.funcoes = funcoes;
        this.ministerios = ministerios;
        this.membros = membros;
        this.periodos = periodos;
        this.leitura = leitura;
        this.relogio = relogio;
    }

    /** As escalas publicadas da pessoa: as próximas em ordem de data e as dos últimos 60 dias, a mais recente antes. */
    @Override
    @Transactional(readOnly = true)
    public MinhasEscalas doMembro(Long usuarioId) {
        var agora = LocalDateTime.now(relogio);
        var desde = agora.toLocalDate().minusDays(DIAS_DAS_PASSADAS);
        var dela = vagas.findByUsuarioId(usuarioId);
        Map<Long, Evento> porEvento =
                porId(eventos.porIds(dela.stream().map(Vaga::getEventoId).collect(Collectors.toSet())), Evento::getId);
        Map<Long, Funcao> porFuncao =
                porId(funcoes.porIds(dela.stream().map(Vaga::getFuncaoId).collect(Collectors.toSet())), Funcao::getId);
        Map<Long, Ministerio> porMinisterio = new HashMap<>();
        var publicadas = dela.stream()
                .filter(vaga -> {
                    var evento = porEvento.get(vaga.getEventoId());
                    return MontagemDaEscala.vigente(vaga, evento, porFuncao.get(vaga.getFuncaoId()))
                            && evento.getPeriodo().isEscalaPublicada()
                            && !evento.getData().isBefore(desde);
                })
                .sorted(Comparator.comparing(
                        vaga -> porEvento.get(vaga.getEventoId()).getInicio()))
                .toList();
        var proximas = publicadas.stream()
                .filter(vaga -> porEvento.get(vaga.getEventoId()).getFim().isAfter(agora))
                .map(vaga -> escala(vaga, porEvento, porFuncao, porMinisterio, agora))
                .toList();
        var passadas = publicadas.reversed().stream()
                .filter(vaga -> !porEvento.get(vaga.getEventoId()).getFim().isAfter(agora))
                .map(vaga -> escala(vaga, porEvento, porFuncao, porMinisterio, agora))
                .toList();
        var dosMinisterios = membros.ministeriosDe(usuarioId).stream()
                .map(ministerio -> new MinhasEscalas.Ministerio(
                        ministerio.getNome(), ministerio.getCor().tint(), "/escalas/" + ministerio.getId()))
                .toList();
        return new MinhasEscalas(proximas, passadas, dosMinisterios);
    }

    /**
     * A grade publicada do ministério no mês. Em rascunho, devolve só que não está publicada.
     *
     * @throws NaoEncontradoException se o ministério não existe ou a pessoa não é membro dele (o admin vê todos)
     */
    @Transactional(readOnly = true)
    public EscalaDoMinisterio doMinisterio(Long ministerioId, YearMonth mes, UsuarioAutenticado usuario) {
        var ministerio = ministerios.buscar(ministerioId);
        if (!usuario.isAdmin() && !membros.participa(usuario.getId(), ministerioId)) {
            throw new NaoEncontradoException("Ministério " + ministerioId + " da pessoa " + usuario.getId());
        }
        String tint = ministerio.getCor().tint();
        boolean publicada = periodos.doMes(ministerioId, mes)
                .filter(periodo -> periodo.isEscalaPublicada())
                .isPresent();
        if (!publicada) {
            return EscalaDoMinisterio.naoPublicada(ministerioId, ministerio.getNome(), tint, mes);
        }
        var pagina = ConsultaDaEscala.paraOMembro(leitura.ler(ministerioId, mes));
        return new EscalaDoMinisterio(
                ministerioId, ministerio.getNome(), tint, mes, true, pagina.funcoes(), pagina.linhas());
    }

    /** Os ministérios de que a pessoa é membro (o admin vê todos), para trocar de escala na página. */
    @Transactional(readOnly = true)
    public List<MinhasEscalas.Ministerio> ministerios(UsuarioAutenticado usuario) {
        return usuario.isAdmin()
                ? ministerios.resumos().stream()
                        .map(resumo -> new MinhasEscalas.Ministerio(
                                resumo.nome(), resumo.cor().tint(), "/escalas/" + resumo.id()))
                        .toList()
                : membros.ministeriosDe(usuario.getId()).stream()
                        .map(ministerio -> new MinhasEscalas.Ministerio(
                                ministerio.getNome(), ministerio.getCor().tint(), "/escalas/" + ministerio.getId()))
                        .toList();
    }

    private MinhasEscalas.Escala escala(
            Vaga vaga,
            Map<Long, Evento> porEvento,
            Map<Long, Funcao> porFuncao,
            Map<Long, Ministerio> porMinisterio,
            LocalDateTime agora) {
        var evento = porEvento.get(vaga.getEventoId());
        var ministerio = porMinisterio.computeIfAbsent(evento.getMinisterioId(), ministerios::buscar);
        return new MinhasEscalas.Escala(
                AjusteDaEscala.quando(evento.getInicio()),
                porFuncao.get(vaga.getFuncaoId()).getNome(),
                evento.getNome(),
                ministerio.getNome(),
                ministerio.getCor().tint(),
                "/escalas/" + ministerio.getId() + "?mes=" + YearMonth.from(evento.getData()),
                vaga.getId(),
                DesistenciaDaEscala.noPrazo(evento.getInicio(), agora));
    }

    private static <T> Map<Long, T> porId(List<T> itens, Function<T, Long> id) {
        return itens.stream().collect(Collectors.toMap(id, Function.identity()));
    }
}
