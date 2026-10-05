package br.igreja.escala.escala.service;

import br.igreja.escala.escala.solver.ValidacaoDaVaga.Violacao;
import java.time.YearMonth;
import java.util.List;

/**
 * Uma vaga aberta para o ajuste manual: onde é, quem está nela e os candidatos da função, quem passa em tudo primeiro.
 *
 * @param quando "12/10 · Dom · 18h00"
 * @param ocupante quem está na vaga, ou nulo
 * @param avisos as regras que quem está na vaga viola agora
 * @param motivoDaVazia por que a vaga obrigatória está vazia (DiagnosticoDaVaga), ou nulo
 * @param bloqueio por que a vaga não se ajusta agora (geração rodando, evento que já começou), ou nulo
 */
public record VagaEmAjuste(
        Long ministerioId,
        Long vagaId,
        long versao,
        YearMonth mes,
        String funcao,
        String quando,
        String evento,
        String ocupante,
        boolean fixada,
        boolean forcada,
        String justificativa,
        List<Violacao> avisos,
        String motivoDaVazia,
        String bloqueio,
        List<Candidato> candidatos) {

    public boolean isEditavel() {
        return bloqueio == null;
    }

    public boolean isVazia() {
        return ocupante == null;
    }

    /** "Projeção, 12/10 · Dom · 18h00". */
    public String titulo() {
        return funcao + ", " + quando;
    }
}
