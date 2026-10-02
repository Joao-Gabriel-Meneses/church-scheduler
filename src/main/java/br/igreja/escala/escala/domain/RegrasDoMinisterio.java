package br.igreja.escala.escala.domain;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Todas as regras de um ministério, uma por tipo do catálogo, na ordem do catálogo. */
public record RegrasDoMinisterio(Map<TipoDeRegra, RegraVigente> porTipo) {

    public RegrasDoMinisterio {
        if (!porTipo.keySet().containsAll(Arrays.asList(TipoDeRegra.values()))) {
            throw new IllegalArgumentException("faltam tipos do catálogo: " + porTipo.keySet());
        }
        porTipo = new EnumMap<>(porTipo);
    }

    /** O catálogo padrão, como o de um ministério recém-criado. */
    public static RegrasDoMinisterio padrao() {
        return de(List.of());
    }

    /** As regras dadas, e o padrão do catálogo para os tipos que faltam. */
    public static RegrasDoMinisterio de(List<RegraVigente> regras) {
        Map<TipoDeRegra, RegraVigente> porTipo = Arrays.stream(TipoDeRegra.values())
                .collect(Collectors.toMap(
                        Function.identity(), TipoDeRegra::padrao, (a, b) -> a, () -> new EnumMap<>(TipoDeRegra.class)));
        regras.forEach(regra -> porTipo.put(regra.tipo(), regra));
        return new RegrasDoMinisterio(porTipo);
    }

    public List<RegraVigente> todas() {
        return List.copyOf(porTipo.values());
    }

    public RegraVigente de(TipoDeRegra tipo) {
        return porTipo.get(tipo);
    }

    public boolean ativa(TipoDeRegra tipo) {
        return de(tipo).ativa();
    }

    public int limitePorMes() {
        return de(TipoDeRegra.LIMITE_POR_PERIODO)
                .parametros(LimitePorPeriodoParams.class)
                .maximo();
    }

    /** O máximo por nível, se a regra está ligada e com nível. */
    public Optional<MaxPorNivelParams> maximoPorNivel() {
        var regra = de(TipoDeRegra.MAX_POR_NIVEL_NO_EVENTO);
        var parametros = regra.parametros(MaxPorNivelParams.class);
        return regra.ativa() && parametros.nivelId() != null ? Optional.of(parametros) : Optional.empty();
    }
}
