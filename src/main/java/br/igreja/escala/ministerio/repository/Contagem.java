package br.igreja.escala.ministerio.repository;

/** Quantidade por id, das consultas agrupadas ({@code select x.id as id, count(...) as total ... group by x.id}). */
public interface Contagem {

    Long getId();

    long getTotal();
}
