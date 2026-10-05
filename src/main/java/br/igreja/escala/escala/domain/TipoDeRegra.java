package br.igreja.escala.escala.domain;

/**
 * O catálogo fixo de regras. Cada tipo é uma restrição do solver (RestricoesDaEscala, com o nome do tipo) e lê os
 * parâmetros da tabela {@code regra}; um ministério que precise de algo novo ganha um tipo novo, sem mexer nos outros.
 * O padrão de cada tipo vale para ministério novo e para tipo que ainda não tem linha no ministério.
 */
public enum TipoDeRegra {
    PESSOAS_POR_FUNCAO(
            "Pessoas por função",
            "Cada evento recebe de cada função até o máximo de pessoas definido em Funções.",
            Rigidez.HARD,
            true,
            new SemParametros()),
    HABILITACAO(
            "Habilitação", "Só serve numa função quem está habilitado nela.", Rigidez.HARD, true, new SemParametros()),
    DISPONIBILIDADE(
            "Disponibilidade",
            "Só serve quem marcou Pode no evento. Sem resposta conta como não pode.",
            Rigidez.HARD,
            true,
            new SemParametros()),
    UMA_FUNCAO_POR_EVENTO(
            "Uma função por evento",
            "Ninguém faz duas funções no mesmo evento.",
            Rigidez.HARD,
            true,
            new SemParametros()),
    SEM_SOBREPOSICAO(
            "Sem horários sobrepostos",
            "Ninguém serve em dois eventos ao mesmo tempo, nem em ministérios diferentes.",
            Rigidez.HARD,
            true,
            new SemParametros()),
    LIMITE_POR_PERIODO(
            "Limite do mês",
            "Cada pessoa serve em no máximo esse número de eventos no mês; dois cultos no mesmo dia contam dois.",
            Rigidez.HARD,
            true,
            new LimitePorPeriodoParams(LimitePorPeriodoParams.PADRAO)),
    MIN_POR_NIVEL_NO_EVENTO(
            "Mínimo por nível",
            "Todo evento com alguém escalado tem pelo menos esse número de pessoas do nível, como um Experiente.",
            Rigidez.HARD,
            false,
            MinPorNivelParams.PADRAO),
    PRIORIDADE_POR_DATA(
            "Eventos mais próximos primeiro",
            "Preenche o máximo de vagas; quando não dá, ficam vazias as dos eventos mais distantes.",
            Rigidez.MEDIUM,
            true,
            new SemParametros()),
    EQUILIBRIO_DE_CARGA(
            "Equilíbrio",
            "Distribui as escalas do mês igualmente entre quem pode servir.",
            Rigidez.SOFT,
            true,
            new SemParametros());

    private final String rotulo;
    private final String descricao;
    private final Rigidez rigidezPadrao;
    private final boolean ativaPorPadrao;
    private final ParametrosDeRegra parametrosPadrao;

    TipoDeRegra(
            String rotulo,
            String descricao,
            Rigidez rigidezPadrao,
            boolean ativaPorPadrao,
            ParametrosDeRegra parametrosPadrao) {
        this.rotulo = rotulo;
        this.descricao = descricao;
        this.rigidezPadrao = rigidezPadrao;
        this.ativaPorPadrao = ativaPorPadrao;
        this.parametrosPadrao = parametrosPadrao;
    }

    /**
     * Regra que não se desliga: sem ela a escala sai errada (habilitação, disponibilidade...) ou vazia (a prioridade é
     * o que faz o solver preencher as vagas).
     */
    public boolean sempreAtiva() {
        return this != LIMITE_POR_PERIODO && this != MIN_POR_NIVEL_NO_EVENTO && this != EQUILIBRIO_DE_CARGA;
    }

    /**
     * O gerente pode pôr alguém que viola esta regra, com justificativa (vaga forçada): o limite do mês e a
     * disponibilidade. Habilitação, uma função por evento, sobreposição e mínimo por nível nunca se forçam.
     */
    public boolean isForcavel() {
        return this == LIMITE_POR_PERIODO || this == DISPONIBILIDADE;
    }

    public String rotulo() {
        return rotulo;
    }

    public String descricao() {
        return descricao;
    }

    public Rigidez rigidezPadrao() {
        return rigidezPadrao;
    }

    public boolean ativaPorPadrao() {
        return ativaPorPadrao;
    }

    public ParametrosDeRegra parametrosPadrao() {
        return parametrosPadrao;
    }

    public Class<? extends ParametrosDeRegra> classeDosParametros() {
        return parametrosPadrao.getClass();
    }

    /** A regra como vem no catálogo, para tipo sem linha no ministério. */
    public RegraVigente padrao() {
        return new RegraVigente(this, rigidezPadrao, 1, ativaPorPadrao, parametrosPadrao);
    }
}
