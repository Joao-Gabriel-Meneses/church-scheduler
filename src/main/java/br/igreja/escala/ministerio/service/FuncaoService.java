package br.igreja.escala.ministerio.service;

import br.igreja.escala.compartilhado.NaoEncontradoException;
import br.igreja.escala.compartilhado.RegraVioladaException;
import br.igreja.escala.ministerio.domain.Funcao;
import br.igreja.escala.ministerio.repository.Contagem;
import br.igreja.escala.ministerio.repository.FuncaoRepository;
import br.igreja.escala.ministerio.repository.HabilitacaoRepository;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Funções de um ministério. Tudo é buscado pelo par (ministério, id): função de outro ministério é 404. */
@Service
public class FuncaoService {

    private final FuncaoRepository funcoes;
    private final HabilitacaoRepository habilitacoes;
    private final MinisterioService ministerios;

    FuncaoService(FuncaoRepository funcoes, HabilitacaoRepository habilitacoes, MinisterioService ministerios) {
        this.funcoes = funcoes;
        this.habilitacoes = habilitacoes;
        this.ministerios = ministerios;
    }

    @Transactional(readOnly = true)
    public List<Funcao> listar(Long ministerioId) {
        return funcoes.findByMinisterioIdOrderByNomeAsc(ministerioId);
    }

    /** As funções do ministério com quantos membros estão habilitados em cada uma. */
    @Transactional(readOnly = true)
    public List<FuncaoResumo> resumos(Long ministerioId) {
        Map<Long, Long> habilitados = habilitacoes.contarPorFuncao(ministerioId).stream()
                .collect(Collectors.toMap(Contagem::getId, Contagem::getTotal));
        return listar(ministerioId).stream()
                .map(funcao -> FuncaoResumo.de(funcao, habilitados.getOrDefault(funcao.getId(), 0L)))
                .toList();
    }

    /**
     * @throws NaoEncontradoException se a função não existe ou é de outro ministério
     */
    @Transactional(readOnly = true)
    public Funcao buscar(Long ministerioId, Long id) {
        return funcoes.findByIdAndMinisterioId(id, ministerioId)
                .orElseThrow(() -> new NaoEncontradoException("Função " + id + " no ministério " + ministerioId));
    }

    @Transactional
    public Funcao criar(Long ministerioId, DadosDaFuncao dados) {
        var ministerio = ministerios.buscar(ministerioId);
        exigirCoerencia(dados);
        if (funcoes.existsByMinisterioIdAndNomeIgnoreCase(
                ministerioId, dados.nome().strip())) {
            throw nomeEmUso(dados);
        }
        return funcoes.save(new Funcao(ministerio, dados.nome(), dados.icone(), dados.qtdMin(), dados.qtdMax()));
    }

    @Transactional
    public Funcao alterar(Long ministerioId, Long id, DadosDaFuncao dados) {
        var funcao = buscar(ministerioId, id);
        exigirCoerencia(dados);
        if (funcoes.existsByMinisterioIdAndNomeIgnoreCaseAndIdNot(
                ministerioId, dados.nome().strip(), id)) {
            throw nomeEmUso(dados);
        }
        funcao.alterar(dados.nome(), dados.icone(), dados.qtdMin(), dados.qtdMax());
        return funcao;
    }

    /**
     * Só exclui função sem habilitações: apagá-las junto tiraria de várias pessoas, sem aviso, o que elas podem fazer.
     *
     * @return a função excluída, para a mensagem de sucesso
     * @throws RegraVioladaException se algum membro está habilitado nela
     */
    @Transactional
    public Funcao excluir(Long ministerioId, Long id) {
        var funcao = buscar(ministerioId, id);
        long habilitados = habilitacoes.countByFuncaoId(id);
        if (habilitados > 0) {
            throw RegraVioladaException.geral(funcao.getNome() + " não foi excluída: "
                    + (habilitados == 1 ? "1 membro está habilitado" : habilitados + " membros estão habilitados")
                    + " nela. Tire a habilitação na página de cada membro e tente de novo.");
        }
        funcoes.delete(funcao);
        return funcao;
    }

    private static void exigirCoerencia(DadosDaFuncao dados) {
        if (dados.qtdMax() < dados.qtdMin()) {
            throw new RegraVioladaException("qtdMax", "O máximo precisa ser igual ou maior que o mínimo.");
        }
    }

    private static RegraVioladaException nomeEmUso(DadosDaFuncao dados) {
        return new RegraVioladaException(
                "nome", "Já existe uma função chamada " + dados.nome().strip() + ".");
    }
}
