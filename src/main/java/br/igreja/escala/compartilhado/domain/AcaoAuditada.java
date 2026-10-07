package br.igreja.escala.compartilhado.domain;

/** O que foi feito. Cada módulo acrescenta as suas ações aqui; o nome vai para a coluna {@code acao}. */
public enum AcaoAuditada {
    NOMEAR_GERENTE,
    REMOVER_GERENTE,
    REDEFINIR_SENHA,
    REMOVER_MEMBRO,
    EDITAR_CONTA,
    DESATIVAR_CONTA,
    REATIVAR_CONTA,
    TRAVAR_DISPONIBILIDADE,
    DESTRAVAR_DISPONIBILIDADE,
    MARCAR_DISPONIBILIDADE,
    ALTERAR_REGRA,
    GERAR_ESCALA,
    AJUSTAR_VAGA,
    FORCAR_VAGA,
    FIXAR_VAGA,
    DESAFIXAR_VAGA,
    PUBLICAR_ESCALA,
    REABRIR_ESCALA,
    DESISTIR_DA_VAGA
}
