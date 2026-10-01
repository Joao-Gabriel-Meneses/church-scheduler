package br.igreja.escala.ministerio.repository;

import br.igreja.escala.ministerio.domain.Habilitacao;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface HabilitacaoRepository extends JpaRepository<Habilitacao, Long> {

    List<Habilitacao> findByFuncaoMinisterioId(Long ministerioId);

    List<Habilitacao> findByUsuarioIdAndFuncaoMinisterioId(Long usuarioId, Long ministerioId);

    Optional<Habilitacao> findByUsuarioIdAndFuncaoId(Long usuarioId, Long funcaoId);

    long countByFuncaoId(Long funcaoId);

    long countByNivelId(Long nivelId);

    @Query("""
            select h.funcao.id as id, count(h) as total
              from Habilitacao h
             where h.funcao.ministerio.id = :ministerioId
             group by h.funcao.id
            """)
    List<Contagem> contarPorFuncao(Long ministerioId);

    @Query("""
            select h.nivel.id as id, count(h) as total
              from Habilitacao h
             where h.nivel.ministerio.id = :ministerioId
             group by h.nivel.id
            """)
    List<Contagem> contarPorNivel(Long ministerioId);
}
