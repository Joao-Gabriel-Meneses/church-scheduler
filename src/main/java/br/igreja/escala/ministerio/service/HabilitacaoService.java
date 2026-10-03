package br.igreja.escala.ministerio.service;

import br.igreja.escala.compartilhado.NaoEncontradoException;
import br.igreja.escala.ministerio.domain.Funcao;
import br.igreja.escala.ministerio.domain.Habilitacao;
import br.igreja.escala.ministerio.repository.FuncaoRepository;
import br.igreja.escala.ministerio.repository.HabilitacaoRepository;
import br.igreja.escala.ministerio.repository.MembresiaRepository;
import br.igreja.escala.ministerio.repository.NivelRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** O que cada membro pode fazer no ministério: um nível em cada função, ou nenhum. */
@Service
public class HabilitacaoService {

    /** Prefixo dos campos do formulário, um por função ("nivel-12"). */
    public static final String CAMPO = "nivel-";

    private final HabilitacaoRepository habilitacoes;
    private final FuncaoRepository funcoes;
    private final NivelRepository niveis;
    private final MembresiaRepository membresias;

    HabilitacaoService(
            HabilitacaoRepository habilitacoes,
            FuncaoRepository funcoes,
            NivelRepository niveis,
            MembresiaRepository membresias) {
        this.habilitacoes = habilitacoes;
        this.funcoes = funcoes;
        this.niveis = niveis;
        this.membresias = membresias;
    }

    /** Uma linha por função do ministério, com o nível atual do membro em cada uma. */
    @Transactional(readOnly = true)
    public List<LinhaDeHabilitacao> doMembro(Long ministerioId, Long usuarioId) {
        Map<Long, Long> nivelPorFuncao =
                habilitacoes.findByUsuarioIdAndFuncaoMinisterioId(usuarioId, ministerioId).stream()
                        .collect(Collectors.toMap(
                                h -> h.getFuncao().getId(), h -> h.getNivel().getId()));
        return funcoes.findByMinisterioIdOrderByNomeAsc(ministerioId).stream()
                .map(funcao -> new LinhaDeHabilitacao(
                        funcao.getId(), funcao.getNome(), funcao.getIcone(), nivelPorFuncao.get(funcao.getId())))
                .toList();
    }

    /** Todas as habilitações do ministério, por pessoa, função e nível. */
    @Transactional(readOnly = true)
    public List<HabilitacaoDaPessoa> doMinisterio(Long ministerioId) {
        return habilitacoes.findByFuncaoMinisterioId(ministerioId).stream()
                .map(habilitacao -> new HabilitacaoDaPessoa(
                        habilitacao.getUsuarioId(),
                        habilitacao.getFuncao().getId(),
                        habilitacao.getNivel().getId()))
                .toList();
    }

    /**
     * Define as habilitações do membro no ministério de uma vez: cada função do ministério fica no nível pedido, ou sem
     * habilitação se ela não vier no mapa (ou vier com nível nulo).
     *
     * @param nivelPorFuncao id da função → id do nível (nulo tira a habilitação)
     * @throws NaoEncontradoException se o membro, alguma função ou algum nível não é deste ministério
     */
    @Transactional
    public void definir(Long ministerioId, Long usuarioId, Map<Long, Long> nivelPorFuncao) {
        if (!membresias.existsByUsuarioIdAndMinisterioId(usuarioId, ministerioId)) {
            throw new NaoEncontradoException("Usuário " + usuarioId + " no ministério " + ministerioId);
        }
        Map<Long, Funcao> doMinisterio = funcoes.findByMinisterioIdOrderByNomeAsc(ministerioId).stream()
                .collect(Collectors.toMap(Funcao::getId, Function.identity()));
        var desconhecidas = new HashSet<>(nivelPorFuncao.keySet());
        desconhecidas.removeAll(doMinisterio.keySet());
        if (!desconhecidas.isEmpty()) {
            throw new NaoEncontradoException("Funções " + desconhecidas + " no ministério " + ministerioId);
        }
        doMinisterio
                .values()
                .forEach(funcao -> definir(ministerioId, usuarioId, funcao, nivelPorFuncao.get(funcao.getId())));
    }

    private void definir(Long ministerioId, Long usuarioId, Funcao funcao, Long nivelId) {
        var atual = habilitacoes.findByUsuarioIdAndFuncaoId(usuarioId, funcao.getId());
        if (nivelId == null) {
            atual.ifPresent(habilitacoes::delete);
            return;
        }
        var nivel = niveis.findByIdAndMinisterioId(nivelId, ministerioId)
                .orElseThrow(() -> new NaoEncontradoException("Nível " + nivelId + " no ministério " + ministerioId));
        atual.ifPresentOrElse(
                habilitacao -> habilitacao.mudarNivel(nivel),
                () -> habilitacoes.save(new Habilitacao(usuarioId, funcao, nivel)));
    }
}
