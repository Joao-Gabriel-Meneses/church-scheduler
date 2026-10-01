package br.igreja.escala.evento.domain;

import java.time.Duration;

/** Limites da duração de um evento. */
public final class Duracoes {

    public static final int MINIMA_EM_MINUTOS = 15;

    /** Um dia inteiro: eventos maiores (um retiro) viram vários eventos. */
    public static final int MAXIMA_EM_MINUTOS = 24 * 60;

    /** A de um culto, sugerida nos formulários e usada para o que já existia antes da duração. */
    public static final int PADRAO_EM_MINUTOS = 120;

    private Duracoes() {}

    static Duration exigirValida(Duration duracao) {
        if (duracao == null
                || duracao.toMinutes() < MINIMA_EM_MINUTOS
                || duracao.toMinutes() > MAXIMA_EM_MINUTOS
                || duracao.toSecondsPart() != 0) {
            throw new IllegalArgumentException("duracao precisa ter de " + MINIMA_EM_MINUTOS + " a " + MAXIMA_EM_MINUTOS
                    + " minutos inteiros: " + duracao);
        }
        return duracao;
    }
}
