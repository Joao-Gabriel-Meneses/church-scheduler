package br.igreja.escala.ministerio.repository;

import br.igreja.escala.ministerio.domain.Habilitacao;
import br.igreja.escala.ministerio.domain.Ministerio;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface HabilitacaoRepository extends JpaRepository<Habilitacao, Long> {

    List<Habilitacao> findByFuncaoMinisterioId(Long ministerioId);

    List<Habilitacao> findByUsuarioIdAndFuncaoMinisterioId(Long usuarioId, Long ministerioId);

    Optional<Habilitacao> findByUsuarioIdAndFuncaoId(Long usuarioId, Long funcaoId);

    boolean existsByUsuarioIdAndFuncaoMinisterioId(Long usuarioId, Long ministerioId);

    long countByFuncaoId(Long funcaoId);

    /** Quem tem habilitação em alguma função do ministério, sem repetir. */
    @Query("""
            select distinct h.usuarioId from Habilitacao h
             where h.funcao.ministerio.id = :ministerioId
            """)
    List<Long> usuariosHabilitados(Long ministerioId);

    /** Ministérios em que a pessoa tem habilitação, sem repetir e sem ordem. */
    @Query("""
            select distinct h.funcao.ministerio from Habilitacao h
             where h.usuarioId = :usuarioId
            """)
    List<Ministerio> ministeriosDe(Long usuarioId);

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
