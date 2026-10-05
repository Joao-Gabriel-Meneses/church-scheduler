package br.igreja.escala.escala.service;

import java.util.Locale;

/**
 * Uma vaga na grade (Slot): a pessoa com as iniciais e o nível na função, ou vazia; fixada, forçada (com a
 * justificativa) ou com aviso (a pessoa viola uma regra como a escala está).
 *
 * @param meta o que vai embaixo do nome: o nível, "Sem habilitação", a regra violada ou "Forçada · regra"
 * @param aviso as regras rígidas que a pessoa viola agora ("LIMITE_POR_PERIODO · DISPONIBILIDADE"), ou nulo
 * @param editavel o gerente pode abrir a vaga para ajustar (evento por vir, sem geração rodando)
 */
public record SlotDaGrade(
        Long vagaId,
        long versao,
        String nome,
        String iniciais,
        String meta,
        boolean vazia,
        boolean obrigatoria,
        boolean fixada,
        boolean forcada,
        String justificativa,
        String aviso,
        boolean editavel) {

    public static SlotDaGrade vazia(Long vagaId, long versao, boolean obrigatoria, boolean fixada, boolean editavel) {
        return new SlotDaGrade(
                vagaId, versao, null, null, null, true, obrigatoria, fixada, false, null, null, editavel);
    }

    public static SlotDaGrade de(
            Long vagaId,
            long versao,
            String nome,
            String nivel,
            boolean fixada,
            boolean forcada,
            String justificativa,
            String aviso,
            boolean editavel) {
        String meta = forcada
                ? (aviso != null ? "Forçada · " + aviso : "Forçada")
                : (aviso != null ? aviso : (nivel == null ? "Sem habilitação" : nivel));
        return new SlotDaGrade(
                vagaId,
                versao,
                nome,
                iniciais(nome),
                meta,
                false,
                true,
                fixada,
                forcada,
                justificativa,
                aviso,
                editavel);
    }

    /** Forçada ou com aviso: triângulo e cor de alerta (estado nunca só por cor: o meta diz a regra). */
    public boolean isAlerta() {
        return forcada || aviso != null;
    }

    /** O nome acessível da vaga na grade: "Ana Souza, Experiente, fixada", "Vaga vazia". */
    public String descricao() {
        if (vazia) {
            return (obrigatoria ? "Vaga vazia" : "Opcional, vazia") + (fixada ? ", fixada" : "");
        }
        return nome + ", " + meta + (fixada && !forcada ? ", fixada" : "")
                + (justificativa != null ? ". Justificativa: " + justificativa : "");
    }

    /** "Ana Souza" → "AS", como no avatar das tabelas. */
    static String iniciais(String nome) {
        String[] partes = nome.strip().split("\\s+");
        String ultima = partes.length > 1 ? partes[partes.length - 1].substring(0, 1) : "";
        return (partes[0].substring(0, 1) + ultima).toUpperCase(Locale.ROOT);
    }
}
