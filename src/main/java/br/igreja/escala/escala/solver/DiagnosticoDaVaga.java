package br.igreja.escala.escala.solver;

import br.igreja.escala.escala.domain.RegrasDoMinisterio;
import br.igreja.escala.escala.domain.Rigidez;
import br.igreja.escala.escala.domain.TipoDeRegra;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Explica uma vaga vazia (o SolutionManager.analyze do Timefold 2 é só da versão paga). Passa quem serve pelas regras
 * rígidas ligadas, na ordem abaixo, e o motivo é a primeira regra que deixa a lista vazia. Lê a escala como está: as
 * outras vagas e os compromissos fixos (inclusive de outros ministérios, sem dizer qual).
 */
public final class DiagnosticoDaVaga {

    private final EscalaDoPeriodo escala;
    private final RegrasDoMinisterio regras;
    private final Map<Long, String> nomesDosNiveis;

    public DiagnosticoDaVaga(EscalaDoPeriodo escala, RegrasDoMinisterio regras, Map<Long, String> nomesDosNiveis) {
        this.escala = escala;
        this.regras = regras;
        this.nomesDosNiveis = nomesDosNiveis;
    }

    public MotivoDaVagaVazia motivo(VagaPlanejada vaga) {
        var funcao = vaga.getFuncao();
        List<Pessoa> candidatas = escala.getPessoas();
        var passos = List.<Passo>of(
                new Passo(
                        TipoDeRegra.HABILITACAO,
                        pessoa -> pessoa.habilitadaEm(funcao.id()),
                        quantas -> "Ninguém está habilitado em " + funcao.nome() + "."),
                new Passo(
                        TipoDeRegra.DISPONIBILIDADE,
                        pessoa -> pessoa.pode(vaga.getEvento().id()),
                        quantas -> "Ninguém habilitado em " + funcao.nome() + " marcou Pode."),
                new Passo(
                        TipoDeRegra.UMA_FUNCAO_POR_EVENTO,
                        pessoa -> outrasVagas(vaga, pessoa)
                                .noneMatch(outra -> outra.getEvento().equals(vaga.getEvento())),
                        quantas -> quem(quantas) + " já " + (quantas == 1 ? "serve" : "servem")
                                + " em outra função neste evento."),
                new Passo(
                        TipoDeRegra.SEM_SOBREPOSICAO,
                        pessoa -> !sobrepoe(vaga, pessoa),
                        quantas -> quem(quantas) + " já " + (quantas == 1 ? "serve" : "servem")
                                + " em outro evento nesse horário."),
                new Passo(
                        TipoDeRegra.LIMITE_POR_PERIODO,
                        pessoa -> eventosNoMes(vaga, pessoa)
                                < escala.getParametros().limitePorMes(),
                        quantas -> quem(quantas) + " já " + (quantas == 1 ? "tem " : "têm ")
                                + escala.getParametros().limitePorMes() + " escalas no mês."),
                new Passo(
                        TipoDeRegra.MAX_POR_NIVEL_NO_EVENTO,
                        pessoa -> cabeNoNivel(vaga, pessoa),
                        quantas -> quem(quantas) + (quantas == 1 ? " é" : " são") + " do nível "
                                + nomesDosNiveis.get(escala.getParametros().nivelLimitado()) + ", e o evento já tem "
                                + escala.getParametros().maximoDoNivel() + "."));
        for (Passo passo : passos) {
            if (!valeComoRigida(passo.regra())) {
                continue;
            }
            var restantes = candidatas.stream().filter(passo.cabe()).toList();
            if (restantes.isEmpty()) {
                return new MotivoDaVagaVazia(passo.regra(), passo.texto().frase(candidatas.size()));
            }
            candidatas = restantes;
        }
        return new MotivoDaVagaVazia(
                null, "Ainda há quem possa servir aqui, mas a geração não achou a tempo. Gere a escala de novo.");
    }

    private boolean valeComoRigida(TipoDeRegra tipo) {
        var regra = regras.de(tipo);
        return regra.ativa() && regra.rigidez() == Rigidez.HARD;
    }

    private Stream<VagaPlanejada> outrasVagas(VagaPlanejada vaga, Pessoa pessoa) {
        return escala.getVagas().stream().filter(outra -> outra != vaga && pessoa.equals(outra.getPessoa()));
    }

    private boolean sobrepoe(VagaPlanejada vaga, Pessoa pessoa) {
        boolean emOutraVaga = outrasVagas(vaga, pessoa)
                .anyMatch(outra -> !outra.getEvento().equals(vaga.getEvento())
                        && seSobrepoem(vaga, outra.getInicio(), outra.getFim()));
        boolean emCompromisso = escala.getCompromissos().stream()
                .anyMatch(compromisso -> compromisso.pessoaId().equals(pessoa.id())
                        && seSobrepoem(vaga, compromisso.inicio(), compromisso.fim()));
        return emOutraVaga || emCompromisso;
    }

    /** Intervalos semiabertos: terminar às 11h00 e começar às 11h00 não se sobrepõem. */
    private static boolean seSobrepoem(VagaPlanejada vaga, LocalDateTime inicio, LocalDateTime fim) {
        return vaga.getInicio().isBefore(fim) && inicio.isBefore(vaga.getFim());
    }

    private long eventosNoMes(VagaPlanejada vaga, Pessoa pessoa) {
        var nasVagas = outrasVagas(vaga, pessoa).map(outra -> outra.getEvento().id());
        var nosCompromissos = escala.getCompromissos().stream()
                .filter(compromisso ->
                        compromisso.contaNoPeriodo() && compromisso.pessoaId().equals(pessoa.id()))
                .map(CompromissoFixo::eventoId);
        return Stream.concat(nasVagas, nosCompromissos).distinct().count();
    }

    private boolean cabeNoNivel(VagaPlanejada vaga, Pessoa pessoa) {
        Long limitado = escala.getParametros().nivelLimitado();
        if (limitado == null || !limitado.equals(pessoa.nivelEm(vaga.getFuncao().id()))) {
            return true;
        }
        long doNivel = escala.getVagas().stream()
                .filter(outra -> outra != vaga && outra.getEvento().equals(vaga.getEvento()))
                .filter(outra -> Objects.equals(limitado, outra.getNivelId()))
                .count();
        return doNivel < escala.getParametros().maximoDoNivel();
    }

    /** "A única pessoa que pode", "As 3 pessoas que podem". */
    private static String quem(int quantas) {
        return quantas == 1 ? "A única pessoa que pode" : "As " + quantas + " pessoas que podem";
    }

    private record Passo(TipoDeRegra regra, Predicate<Pessoa> cabe, Frase texto) {}

    @FunctionalInterface
    private interface Frase {
        String frase(int quantas);
    }
}
