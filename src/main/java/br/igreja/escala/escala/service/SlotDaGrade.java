package br.igreja.escala.escala.service;

import java.util.Locale;

/**
 * Uma vaga na grade (Slot): a pessoa com as iniciais e o nível na função, ou vazia. Fixada e forçada (Fase 3b) já têm
 * o estado próprio.
 *
 * @param meta o que vai embaixo do nome: o nível, "Forçada" ou "Sem habilitação"
 */
public record SlotDaGrade(
        String nome,
        String iniciais,
        String meta,
        boolean vazia,
        boolean obrigatoria,
        boolean fixada,
        boolean forcada) {

    public static SlotDaGrade vazia(boolean obrigatoria) {
        return new SlotDaGrade(null, null, null, true, obrigatoria, false, false);
    }

    public static SlotDaGrade de(String nome, String nivel, boolean fixada, boolean forcada) {
        String meta = forcada ? "Forçada" : (nivel == null ? "Sem habilitação" : nivel);
        return new SlotDaGrade(nome, iniciais(nome), meta, false, true, fixada, forcada);
    }

    /** "Ana Souza" → "AS", como no avatar das tabelas. */
    static String iniciais(String nome) {
        String[] partes = nome.strip().split("\\s+");
        String ultima = partes.length > 1 ? partes[partes.length - 1].substring(0, 1) : "";
        return (partes[0].substring(0, 1) + ultima).toUpperCase(Locale.ROOT);
    }
}
