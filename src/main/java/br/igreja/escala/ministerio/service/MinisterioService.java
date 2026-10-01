package br.igreja.escala.ministerio.service;

import br.igreja.escala.compartilhado.NaoEncontradoException;
import br.igreja.escala.compartilhado.RegraVioladaException;
import br.igreja.escala.identidade.service.UsuarioResumo;
import br.igreja.escala.identidade.service.UsuarioService;
import br.igreja.escala.ministerio.domain.Membresia;
import br.igreja.escala.ministerio.domain.Ministerio;
import br.igreja.escala.ministerio.repository.MembresiaRepository;
import br.igreja.escala.ministerio.repository.MembresiaRepository.MembrosPorMinisterio;
import br.igreja.escala.ministerio.repository.MinisterioRepository;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Ministérios: o admin cria e altera; os outros módulos consultam. */
@Service
public class MinisterioService {

    private final MinisterioRepository ministerios;
    private final MembresiaRepository membresias;
    private final UsuarioService usuarios;

    MinisterioService(MinisterioRepository ministerios, MembresiaRepository membresias, UsuarioService usuarios) {
        this.ministerios = ministerios;
        this.membresias = membresias;
        this.usuarios = usuarios;
    }

    /**
     * @throws NaoEncontradoException se o ministério não existe
     */
    @Transactional(readOnly = true)
    public Ministerio buscar(Long id) {
        return ministerios.findById(id).orElseThrow(() -> new NaoEncontradoException("Ministério " + id));
    }

    /** Todos os ministérios, em ordem de nome, com gerentes e quantidade de membros. */
    @Transactional(readOnly = true)
    public List<MinisterioResumo> resumos() {
        Map<Long, Long> membrosPorMinisterio = membresias.contarMembrosPorMinisterio().stream()
                .collect(Collectors.toMap(MembrosPorMinisterio::getMinisterioId, MembrosPorMinisterio::getTotal));
        Map<Long, List<Long>> gerentesPorMinisterio = membresias.findByGerenteTrue().stream()
                .collect(Collectors.groupingBy(
                        membresia -> membresia.getMinisterio().getId(),
                        Collectors.mapping(Membresia::getUsuarioId, Collectors.toList())));
        var nomes = usuarios.resumosPorId(
                gerentesPorMinisterio.values().stream().flatMap(List::stream).collect(Collectors.toSet()));
        return ministerios.findAllByOrderByNomeAsc().stream()
                .map(ministerio -> new MinisterioResumo(
                        ministerio.getId(),
                        ministerio.getNome(),
                        ministerio.getCor(),
                        ministerio.getIcone(),
                        gerentesPorMinisterio.getOrDefault(ministerio.getId(), List.of()).stream()
                                .map(nomes::get)
                                .map(UsuarioResumo::nome)
                                .sorted(String.CASE_INSENSITIVE_ORDER)
                                .toList(),
                        membrosPorMinisterio.getOrDefault(ministerio.getId(), 0L)))
                .toList();
    }

    /**
     * @throws RegraVioladaException se já existe um ministério com esse nome
     */
    @Transactional
    public Ministerio criar(DadosDoMinisterio dados) {
        if (ministerios.existsByNomeIgnoreCase(dados.nome().strip())) {
            throw nomeEmUso(dados);
        }
        return ministerios.save(new Ministerio(dados.nome(), dados.cor(), dados.icone()));
    }

    /**
     * @throws RegraVioladaException se outro ministério já tem esse nome
     */
    @Transactional
    public Ministerio alterar(Long id, DadosDoMinisterio dados) {
        var ministerio = buscar(id);
        if (ministerios.existsByNomeIgnoreCaseAndIdNot(dados.nome().strip(), id)) {
            throw nomeEmUso(dados);
        }
        ministerio.alterar(dados.nome(), dados.cor(), dados.icone());
        return ministerio;
    }

    private static RegraVioladaException nomeEmUso(DadosDoMinisterio dados) {
        return new RegraVioladaException(
                "nome", "Já existe um ministério chamado " + dados.nome().strip() + ".");
    }
}
