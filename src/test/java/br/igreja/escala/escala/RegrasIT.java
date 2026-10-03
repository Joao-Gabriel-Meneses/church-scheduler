package br.igreja.escala.escala;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.igreja.escala.TesteDeIntegracao;
import br.igreja.escala.escala.domain.LimitePorPeriodoParams;
import br.igreja.escala.escala.domain.Regra;
import br.igreja.escala.escala.domain.TipoDeRegra;
import br.igreja.escala.escala.repository.RegraRepository;
import br.igreja.escala.escala.service.RegraService;
import br.igreja.escala.identidade.domain.Usuario;
import br.igreja.escala.identidade.domain.UsuarioAutenticado;
import br.igreja.escala.identidade.repository.UsuarioRepository;
import br.igreja.escala.ministerio.domain.CorDoMinisterio;
import br.igreja.escala.ministerio.domain.Icone;
import br.igreja.escala.ministerio.service.DadosDoMinisterio;
import br.igreja.escala.ministerio.service.MinisterioService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** Regras no Oracle: o catálogo gravado ao criar o ministério, o JSON em CLOB e as constraints. */
@TesteDeIntegracao
@Transactional
class RegrasIT {

    @Autowired
    MinisterioService ministerios;

    @Autowired
    RegraService servico;

    @Autowired
    RegraRepository regras;

    @Autowired
    UsuarioRepository usuarios;

    @Autowired
    EntityManager entityManager;

    @Autowired
    JdbcTemplate jdbc;

    private Long midiaId;

    @BeforeEach
    void criaOMinisterioPeloServico() {
        midiaId = ministerios
                .criar(new DadosDoMinisterio("Mídia Regras", CorDoMinisterio.MINT, Icone.MONITOR))
                .getId();
    }

    @Test
    void ministerioCriadoPeloServicoGanhaOCatalogoPadrao() {
        entityManager.flush();

        assertThat(regras.findByMinisterioId(midiaId))
                .extracting(Regra::getTipo)
                .containsExactlyInAnyOrder(TipoDeRegra.values());
        assertThat(jdbc.queryForObject(
                        "select json_value(parametros, '$.maximo') from regra where ministerio_id = ? and tipo = ?",
                        String.class,
                        midiaId,
                        "LIMITE_POR_PERIODO"))
                .isEqualTo("3");
        assertThat(jdbc.queryForObject(
                        "select count(*) from regra where ministerio_id = ? and ativa = 0", Integer.class, midiaId))
                .as("só o máximo por nível nasce desligado")
                .isEqualTo(1);
    }

    @Test
    void limiteAlteradoVoltaDoBancoComOJsonNovo() {
        var gerente = new UsuarioAutenticado(
                usuarios.save(Usuario.membro("Paula Regras", "paula.regras@teste.local", "{noop}x")));

        servico.alterarLimite(midiaId, 5, gerente);
        entityManager.flush();
        entityManager.clear();

        assertThat(servico.doMinisterio(midiaId).limitePorMes()).isEqualTo(5);
        assertThat(regras.findByMinisterioIdAndTipo(midiaId, TipoDeRegra.LIMITE_POR_PERIODO)
                        .orElseThrow()
                        .getParametros())
                .isEqualTo(new LimitePorPeriodoParams(5));
    }

    @Test
    void parametrosPrecisamSerJson() {
        entityManager.flush();

        assertThatThrownBy(() -> jdbc.update(
                        "update regra set parametros = 'maximo: 3' where ministerio_id = ? and tipo = ?",
                        midiaId,
                        "LIMITE_POR_PERIODO"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("CK_REGRA_PARAMETROS");
    }

    @Test
    void umaLinhaPorTipoNoMinisterio() {
        entityManager.flush();

        assertThatThrownBy(() -> regras.saveAndFlush(new Regra(midiaId, TipoDeRegra.HABILITACAO)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("UK_REGRA_TIPO");
    }
}
