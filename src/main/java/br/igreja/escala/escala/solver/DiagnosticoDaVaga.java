package br.igreja.escala.escala.solver;

import br.igreja.escala.escala.domain.RegrasDoMinisterio;
import br.igreja.escala.escala.domain.Rigidez;
import br.igreja.escala.escala.domain.TipoDeRegra;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
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
                                + escalas(escala.getParametros().limitePorMes()) + " no mês."),
                new Passo(
                        TipoDeRegra.MIN_POR_NIVEL_NO_EVENTO,
                        pessoa -> completaONivel(vaga, pessoa),
                        quantas -> faltaDoNivel(vaga, quantas)));
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

    /**
     * Com a pessoa na vaga, o evento passa a ter alguém escalado e precisa do mínimo do nível exigido: cabe se as outras
     * vagas do evento, mais ela, chegam ao mínimo.
     */
    private boolean completaONivel(VagaPlanejada vaga, Pessoa pessoa) {
        Long exigido = escala.getParametros().nivelExigido();
        if (exigido == null) {
            return true;
        }
        int dela = exigido.equals(pessoa.nivelEm(vaga.getFuncao().id())) ? 1 : 0;
        return doNivelNasOutras(vaga) + dela >= escala.getParametros().minimoDoNivel();
    }

    private long doNivelNasOutras(VagaPlanejada vaga) {
        Long exigido = escala.getParametros().nivelExigido();
        return escala.getVagas().stream()
                .filter(outra -> outra != vaga && outra.getEvento().equals(vaga.getEvento()))
                .filter(outra -> Objects.equals(exigido, outra.getNivelId()))
                .count();
    }

    /**
     * "O evento precisa de pelo menos 1 pessoa do nível Experiente, e a única pessoa que pode não é desse nível." Se
     * falta mais de uma, nenhuma pessoa sozinha completa.
     */
    private String faltaDoNivel(VagaPlanejada vaga, int quantas) {
        int minimo = escala.getParametros().minimoDoNivel();
        long tem = doNivelNasOutras(vaga);
        String precisa = "O evento precisa de pelo menos " + minimo + (minimo == 1 ? " pessoa" : " pessoas")
                + " do nível " + nomesDosNiveis.get(escala.getParametros().nivelExigido());
        if (minimo - tem > 1) {
            return precisa + " e tem " + tem + ": uma pessoa sozinha não completa.";
        }
        return precisa + ", e " + quem(quantas).toLowerCase(Locale.ROOT) + (quantas == 1 ? " não é" : " não são")
                + " desse nível.";
    }

    /** "1 escala", "3 escalas". */
    private static String escalas(int quantas) {
        return quantas + (quantas == 1 ? " escala" : " escalas");
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
