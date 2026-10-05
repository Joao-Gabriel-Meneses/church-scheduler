package br.igreja.escala.escala;

import static br.igreja.escala.evento.ExemplosDeEvento.DUAS_HORAS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;

import br.igreja.escala.TesteDeIntegracao;
import br.igreja.escala.compartilhado.Fuso;
import br.igreja.escala.escala.domain.Vaga;
import br.igreja.escala.escala.repository.VagaRepository;
import br.igreja.escala.evento.domain.Evento;
import br.igreja.escala.evento.domain.Periodo;
import br.igreja.escala.evento.repository.EventoRepository;
import br.igreja.escala.evento.repository.PeriodoRepository;
import br.igreja.escala.identidade.domain.Usuario;
import br.igreja.escala.identidade.repository.UsuarioRepository;
import br.igreja.escala.ministerio.domain.CorDoMinisterio;
import br.igreja.escala.ministerio.domain.Funcao;
import br.igreja.escala.ministerio.domain.Icone;
import br.igreja.escala.ministerio.domain.Ministerio;
import br.igreja.escala.ministerio.repository.FuncaoRepository;
import br.igreja.escala.ministerio.repository.MinisterioRepository;
import jakarta.persistence.EntityManager;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** Vagas no Oracle: uma por evento, função e posição; forçada só com justificativa; somem com a função. */
@TesteDeIntegracao
@Transactional
class VagasIT {

    private static final YearMonth MES = YearMonth.now(Fuso.SAO_PAULO).plusMonths(2);

    @Autowired
    MinisterioRepository ministerios;

    @Autowired
    FuncaoRepository funcoes;

    @Autowired
    PeriodoRepository periodos;

    @Autowired
    EventoRepository eventos;

    @Autowired
    UsuarioRepository usuarios;

    @Autowired
    VagaRepository vagas;

    @Autowired
    EntityManager entityManager;

    @Autowired
    JdbcTemplate jdbc;

    private Long eventoId;
    private Long funcaoId;
    private Long anaId;

    @BeforeEach
    void criaOEvento() {
        var midia = ministerios.save(new Ministerio("Mídia Vagas", CorDoMinisterio.MINT, Icone.MONITOR));
        funcaoId =
                funcoes.save(new Funcao(midia, "Projeção", Icone.MONITOR, 1, 2)).getId();
        var periodo = periodos.save(new Periodo(midia.getId(), MES));
        eventoId = eventos.save(Evento.avulso(periodo, "Ensaio", MES.atDay(10), LocalTime.of(19, 0), DUAS_HORAS))
                .getId();
        anaId = usuarios.save(Usuario.membro("Ana Vagas", "ana.vagas@teste.local", "{noop}x"))
                .getId();
    }

    @Test
    void vagaGuardaAPessoaEVoltaDoBanco() {
        var vaga = new Vaga(eventoId, funcaoId, 1);
        vaga.escalar(anaId);
        vagas.save(vaga);
        vagas.save(new Vaga(eventoId, funcaoId, 2));
        entityManager.flush();
        entityManager.clear();

        assertThat(vagas.findByEventoIdIn(List.of(eventoId)))
                .extracting(Vaga::getPosicao, Vaga::getUsuarioId)
                .containsExactlyInAnyOrder(tuple(1, anaId), tuple(2, null));
        assertThat(vagas.findByEventoIdInAndUsuarioIdIsNotNull(List.of(eventoId)))
                .singleElement()
                .extracting(Vaga::getUsuarioId)
                .isEqualTo(anaId);
    }

    @Test
    void umaVagaPorEventoFuncaoEPosicao() {
        vagas.saveAndFlush(new Vaga(eventoId, funcaoId, 1));

        assertThatThrownBy(() -> vagas.saveAndFlush(new Vaga(eventoId, funcaoId, 1)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("UK_VAGA");
    }

    @Test
    void forcadaSoComPessoaEJustificativa() {
        var vaga = vagas.saveAndFlush(new Vaga(eventoId, funcaoId, 1));

        assertThatThrownBy(() -> jdbc.update("update vaga set forcada = 1, fixada = 1 where id = ?", vaga.getId()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("CK_VAGA_FORCADA_JUSTIFICADA");
        jdbc.update(
                "update vaga set forcada = 1, fixada = 1, usuario_id = ?, justificativa = 'Só ela opera a mesa nova'"
                        + " where id = ?",
                anaId,
                vaga.getId());
    }

    @Test
    void forcadaESempreFixada() {
        var vaga = vagas.saveAndFlush(new Vaga(eventoId, funcaoId, 1));

        assertThatThrownBy(() -> jdbc.update(
                        "update vaga set forcada = 1, fixada = 0, usuario_id = ?, justificativa = 'Motivo' where id = ?",
                        anaId,
                        vaga.getId()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("CK_VAGA_FORCADA_FIXADA");
    }

    @Test
    void vagasSaemComAFuncaoExcluida() {
        vagas.saveAndFlush(new Vaga(eventoId, funcaoId, 1));

        jdbc.update("delete from funcao where id = ?", funcaoId);

        assertThat(jdbc.queryForObject("select count(*) from vaga where evento_id = ?", Integer.class, eventoId))
                .isZero();
    }
}
