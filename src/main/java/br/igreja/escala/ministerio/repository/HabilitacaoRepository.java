package br.igreja.escala.ministerio.repository;

import br.igreja.escala.ministerio.domain.Habilitacao;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HabilitacaoRepository extends JpaRepository<Habilitacao, Long> {

    List<Habilitacao> findByFuncaoMinisterioId(Long ministerioId);

    List<Habilitacao> findByUsuarioIdAndFuncaoMinisterioId(Long usuarioId, Long ministerioId);

    Optional<Habilitacao> findByUsuarioIdAndFuncaoId(Long usuarioId, Long funcaoId);

    long countByFuncaoId(Long funcaoId);

    long countByNivelId(Long nivelId);
}
