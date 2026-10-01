package br.igreja.escala.ministerio.service;

import br.igreja.escala.compartilhado.NaoEncontradoException;
import br.igreja.escala.compartilhado.RegraVioladaException;
import br.igreja.escala.ministerio.domain.Nivel;
import br.igreja.escala.ministerio.repository.Contagem;
import br.igreja.escala.ministerio.repository.HabilitacaoRepository;
import br.igreja.escala.ministerio.repository.NivelRepository;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Níveis de um ministério, do menos ao mais experiente. Buscados pelo par (ministério, id). */
@Service
public class NivelService {

    private final NivelRepository niveis;
    private final HabilitacaoRepository habilitacoes;
    private final MinisterioService ministerios;

    NivelService(NivelRepository niveis, HabilitacaoRepository habilitacoes, MinisterioService ministerios) {
        this.niveis = niveis;
        this.habilitacoes = habilitacoes;
        this.ministerios = ministerios;
    }

    @Transactional(readOnly = true)
    public List<Nivel> listar(Long ministerioId) {
        return niveis.findByMinisterioIdOrderByOrdemAsc(ministerioId);
    }

    @Transactional(readOnly = true)
    public List<NivelResumo> resumos(Long ministerioId) {
        Map<Long, Long> porNivel = habilitacoes.contarPorNivel(ministerioId).stream()
                .collect(Collectors.toMap(Contagem::getId, Contagem::getTotal));
        return listar(ministerioId).stream()
                .map(nivel -> NivelResumo.de(nivel, porNivel.getOrDefault(nivel.getId(), 0L)))
                .toList();
    }

    /** Ordem sugerida para o próximo nível: logo depois do mais experiente. */
    @Transactional(readOnly = true)
    public int proximaOrdem(Long ministerioId) {
        return listar(ministerioId).stream().mapToInt(Nivel::getOrdem).max().orElse(0) + 1;
    }

    /**
     * @throws NaoEncontradoException se o nível não existe ou é de outro ministério
     */
    @Transactional(readOnly = true)
    public Nivel buscar(Long ministerioId, Long id) {
        return niveis.findByIdAndMinisterioId(id, ministerioId)
                .orElseThrow(() -> new NaoEncontradoException("Nível " + id + " no ministério " + ministerioId));
    }

    @Transactional
    public Nivel criar(Long ministerioId, DadosDoNivel dados) {
        var ministerio = ministerios.buscar(ministerioId);
        exigirNomeEOrdemLivres(ministerioId, null, dados);
        return niveis.save(new Nivel(ministerio, dados.nome(), dados.ordem()));
    }

    @Transactional
    public Nivel alterar(Long ministerioId, Long id, DadosDoNivel dados) {
        var nivel = buscar(ministerioId, id);
        exigirNomeEOrdemLivres(ministerioId, id, dados);
        nivel.alterar(dados.nome(), dados.ordem());
        return nivel;
    }

    /**
     * @throws RegraVioladaException se alguma habilitação usa o nível
     */
    @Transactional
    public Nivel excluir(Long ministerioId, Long id) {
        var nivel = buscar(ministerioId, id);
        long emUso = habilitacoes.countByNivelId(id);
        if (emUso > 0) {
            throw RegraVioladaException.geral(nivel.getNome() + " não foi excluído: "
                    + (emUso == 1 ? "1 habilitação usa" : emUso + " habilitações usam")
                    + " este nível. Mude o nível na página de cada membro e tente de novo.");
        }
        niveis.delete(nivel);
        return nivel;
    }

    private void exigirNomeEOrdemLivres(Long ministerioId, Long id, DadosDoNivel dados) {
        String nome = dados.nome().strip();
        boolean nomeEmUso = id == null
                ? niveis.existsByMinisterioIdAndNomeIgnoreCase(ministerioId, nome)
                : niveis.existsByMinisterioIdAndNomeIgnoreCaseAndIdNot(ministerioId, nome, id);
        if (nomeEmUso) {
            throw new RegraVioladaException("nome", "Já existe um nível chamado " + nome + ".");
        }
        niveis.findByMinisterioIdAndOrdem(ministerioId, dados.ordem())
                .filter(outro -> !outro.getId().equals(id))
                .ifPresent(outro -> {
                    throw new RegraVioladaException(
                            "ordem", "A ordem " + dados.ordem() + " já é do nível " + outro.getNome() + ".");
                });
    }
}
