package br.igreja.escala.escala.service;

import br.igreja.escala.compartilhado.RegraVioladaException;
import br.igreja.escala.compartilhado.domain.AcaoAuditada;
import br.igreja.escala.compartilhado.domain.RegistroDeAuditoria;
import br.igreja.escala.compartilhado.service.AuditoriaService;
import br.igreja.escala.escala.domain.LimitePorPeriodoParams;
import br.igreja.escala.escala.domain.MaxPorNivelParams;
import br.igreja.escala.escala.domain.Regra;
import br.igreja.escala.escala.domain.RegraVigente;
import br.igreja.escala.escala.domain.RegrasDoMinisterio;
import br.igreja.escala.escala.domain.TipoDeRegra;
import br.igreja.escala.escala.repository.RegraRepository;
import br.igreja.escala.identidade.domain.UsuarioAutenticado;
import br.igreja.escala.ministerio.domain.Nivel;
import br.igreja.escala.ministerio.service.MinisterioCriado;
import br.igreja.escala.ministerio.service.MinisterioService;
import br.igreja.escala.ministerio.service.NivelService;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * As regras de cada ministério. O ministério novo recebe o catálogo padrão; o gerente muda o limite do mês e o máximo
 * por nível, com auditoria. Tipo sem linha (ministério criado antes do catálogo, ou tipo novo) vale o padrão.
 */
@Service
public class RegraService {

    private final RegraRepository regras;
    private final MinisterioService ministerios;
    private final NivelService niveis;
    private final AuditoriaService auditoria;

    RegraService(
            RegraRepository regras, MinisterioService ministerios, NivelService niveis, AuditoriaService auditoria) {
        this.regras = regras;
        this.ministerios = ministerios;
        this.niveis = niveis;
        this.auditoria = auditoria;
    }

    /** Grava o catálogo padrão na transação em que o ministério é criado. */
    @EventListener
    @Transactional
    public void aoCriarMinisterio(MinisterioCriado criado) {
        for (TipoDeRegra tipo : TipoDeRegra.values()) {
            regras.save(new Regra(criado.ministerioId(), tipo));
        }
    }

    /**
     * As regras que valem para a geração. O máximo por nível cujo nível foi excluído vale como desligado.
     *
     * @throws br.igreja.escala.compartilhado.NaoEncontradoException se o ministério não existe
     */
    @Transactional(readOnly = true)
    public RegrasDoMinisterio doMinisterio(Long ministerioId) {
        ministerios.buscar(ministerioId);
        var doMinisterio = niveisPorId(ministerioId);
        return RegrasDoMinisterio.de(regras.findByMinisterioId(ministerioId).stream()
                .map(Regra::vigente)
                .map(regra -> semNivelExcluido(regra, doMinisterio))
                .toList());
    }

    /** As regras na ordem do catálogo, com o que cada uma faz no ministério, para a página de regras. */
    @Transactional(readOnly = true)
    public List<RegraResumo> resumos(Long ministerioId) {
        var doMinisterio = niveisPorId(ministerioId);
        var configuradas = regras.findByMinisterioId(ministerioId).stream()
                .collect(Collectors.toMap(Regra::getTipo, Regra::vigente));
        var vigentes = doMinisterio(ministerioId);
        return vigentes.todas().stream()
                .map(vigente -> resumo(vigente, configuradas.getOrDefault(vigente.tipo(), vigente), doMinisterio))
                .toList();
    }

    /**
     * @return se mudou; falso se o limite já era esse
     * @throws RegraVioladaException no campo {@code maximo}, fora de 1 a 31
     */
    @Transactional
    public boolean alterarLimite(Long ministerioId, int maximo, UsuarioAutenticado autor) {
        var ministerio = ministerios.buscar(ministerioId);
        if (maximo < LimitePorPeriodoParams.MINIMO || maximo > LimitePorPeriodoParams.MAXIMO) {
            throw new RegraVioladaException(
                    "maximo",
                    "O limite vai de " + LimitePorPeriodoParams.MINIMO + " a " + LimitePorPeriodoParams.MAXIMO
                            + " eventos no mês.");
        }
        var regra = linha(ministerioId, TipoDeRegra.LIMITE_POR_PERIODO);
        int antes = ((LimitePorPeriodoParams) regra.getParametros()).maximo();
        if (antes == maximo) {
            return false;
        }
        regra.alterar(new LimitePorPeriodoParams(maximo), true);
        auditoria.registrar(new RegistroDeAuditoria(
                AcaoAuditada.ALTERAR_REGRA,
                autor.getId(),
                ministerioId,
                null,
                "Limite do mês: de " + antes + " para " + maximo + " eventos por pessoa (" + ministerio.getNome()
                        + ")."));
        return true;
    }

    /**
     * Liga (com nível e máximo) ou desliga o máximo por nível no evento.
     *
     * @return se mudou
     * @throws RegraVioladaException no campo {@code nivelId}, se liga sem nível, ou {@code maximo}, fora de 1 a 20
     * @throws br.igreja.escala.compartilhado.NaoEncontradoException se o nível é de outro ministério
     */
    @Transactional
    public boolean alterarMaximoPorNivel(
            Long ministerioId, boolean ligada, Long nivelId, int maximo, UsuarioAutenticado autor) {
        var ministerio = ministerios.buscar(ministerioId);
        if (maximo < MaxPorNivelParams.MINIMO || maximo > MaxPorNivelParams.MAXIMO) {
            throw new RegraVioladaException(
                    "maximo",
                    "O máximo vai de " + MaxPorNivelParams.MINIMO + " a " + MaxPorNivelParams.MAXIMO
                            + " pessoas por evento.");
        }
        if (ligada && nivelId == null) {
            throw new RegraVioladaException("nivelId", "Escolha o nível.");
        }
        Nivel nivel = nivelId == null ? null : niveis.buscar(ministerioId, nivelId);
        var regra = linha(ministerioId, TipoDeRegra.MAX_POR_NIVEL_NO_EVENTO);
        var novos = new MaxPorNivelParams(nivelId, maximo);
        if (regra.isAtiva() == ligada && regra.getParametros().equals(novos)) {
            return false;
        }
        regra.alterar(novos, ligada);
        auditoria.registrar(new RegistroDeAuditoria(
                AcaoAuditada.ALTERAR_REGRA,
                autor.getId(),
                ministerioId,
                null,
                (ligada
                                ? "Máximo por nível ligado: " + quantasDoNivel(maximo, nivel.getNome()) + " por evento"
                                : "Máximo por nível desligado")
                        + " (" + ministerio.getNome() + ")."));
        return true;
    }

    /** A linha do tipo no ministério, criada com o padrão se ainda não existe. */
    private Regra linha(Long ministerioId, TipoDeRegra tipo) {
        return regras.findByMinisterioIdAndTipo(ministerioId, tipo)
                .orElseGet(() -> regras.save(new Regra(ministerioId, tipo)));
    }

    private Map<Long, Nivel> niveisPorId(Long ministerioId) {
        return niveis.listar(ministerioId).stream().collect(Collectors.toMap(Nivel::getId, Function.identity()));
    }

    private static RegraVigente semNivelExcluido(RegraVigente regra, Map<Long, Nivel> doMinisterio) {
        if (regra.tipo() != TipoDeRegra.MAX_POR_NIVEL_NO_EVENTO || !regra.ativa()) {
            return regra;
        }
        Long nivelId = regra.parametros(MaxPorNivelParams.class).nivelId();
        if (nivelId != null && doMinisterio.containsKey(nivelId)) {
            return regra;
        }
        return new RegraVigente(regra.tipo(), regra.rigidez(), regra.peso(), false, regra.parametros());
    }

    private static RegraResumo resumo(RegraVigente vigente, RegraVigente configurada, Map<Long, Nivel> niveis) {
        var tipo = vigente.tipo();
        String descricao = tipo.descricao();
        String edicao = null;
        if (tipo == TipoDeRegra.LIMITE_POR_PERIODO) {
            int maximo = vigente.parametros(LimitePorPeriodoParams.class).maximo();
            descricao = "Cada pessoa serve em no máximo " + maximo + (maximo == 1 ? " evento" : " eventos")
                    + " no mês; dois cultos no mesmo dia contam dois.";
            edicao = "limite";
        } else if (tipo == TipoDeRegra.MAX_POR_NIVEL_NO_EVENTO) {
            var parametros = vigente.parametros(MaxPorNivelParams.class);
            if (vigente.ativa()) {
                descricao = "No máximo "
                        + quantasDoNivel(
                                parametros.maximo(),
                                niveis.get(parametros.nivelId()).getNome()) + " por evento.";
            } else if (configurada.ativa()) {
                descricao = "Sem efeito: o nível escolhido foi excluído. Escolha outro nível.";
            }
            edicao = "maximo-por-nivel";
        }
        String estado = tipo.sempreAtiva() ? "Sempre ativa" : (vigente.ativa() ? "Ligada" : "Desligada");
        return new RegraResumo(
                tipo.name(), tipo.rotulo(), descricao, vigente.rigidez().rotulo(), estado, vigente.ativa(), edicao);
    }

    /** "1 pessoa do nível Iniciante", "2 pessoas do nível Iniciante". */
    private static String quantasDoNivel(int maximo, String nivel) {
        return maximo + (maximo == 1 ? " pessoa" : " pessoas") + " do nível " + nivel;
    }
}
