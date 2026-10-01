package br.igreja.escala.disponibilidade;

import static br.igreja.escala.evento.ExemplosDeEvento.DUAS_HORAS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.igreja.escala.TesteDeIntegracao;
import br.igreja.escala.compartilhado.Fuso;
import br.igreja.escala.disponibilidade.domain.Disponibilidade;
import br.igreja.escala.disponibilidade.domain.Resposta;
import br.igreja.escala.disponibilidade.repository.DisponibilidadeRepository;
import br.igreja.escala.evento.domain.Evento;
import br.igreja.escala.evento.domain.Periodo;
import br.igreja.escala.evento.repository.EventoRepository;
import br.igreja.escala.evento.repository.PeriodoRepository;
import br.igreja.escala.identidade.domain.Usuario;
import br.igreja.escala.identidade.repository.UsuarioRepository;
import br.igreja.escala.ministerio.domain.CorDoMinisterio;
import br.igreja.escala.ministerio.domain.Icone;
import br.igreja.escala.ministerio.domain.Ministerio;
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

/** Disponibilidade no Oracle: mapeamento e constraints. */
@TesteDeIntegracao
@Transactional
class DisponibilidadeIT {

    /** Daqui a dois meses: todos os eventos estão por vir, qualquer que seja o dia em que o teste roda. */
    private static final YearMonth MES = YearMonth.now(Fuso.SAO_PAULO).plusMonths(2);

    @Autowired
    DisponibilidadeRepository disponibilidades;

    @Autowired
    MinisterioRepository ministerios;

    @Autowired
    PeriodoRepository periodos;

    @Autowired
    EventoRepository eventos;

    @Autowired
    UsuarioRepository usuarios;

    @Autowired
    EntityManager entityManager;

    @Autowired
    JdbcTemplate jdbc;

    private Usuario ana;
    private Evento ensaio;

    @BeforeEach
    void criaOsDados() {
        var midia = ministerios.save(new Ministerio("Mídia Disponibilidade", CorDoMinisterio.MINT, Icone.MONITOR));
        var periodo = periodos.save(new Periodo(midia.getId(), MES));
        ensaio = eventos.save(Evento.avulso(periodo, "Ensaio", MES.atDay(14), LocalTime.of(15, 30), DUAS_HORAS));
        ana = usuarios.save(Usuario.membro("Ana Disponibilidade", "ana.disponibilidade@teste.local", "{noop}x"));
    }

    @Test
    void guardaARespostaComODiaEOHorarioDoEvento() {
        var salva = disponibilidades.save(new Disponibilidade(ana.getId(), ensaio, Resposta.NAO_PODE, ana.getId()));
        entityManager.flush();
        entityManager.clear();

        var lida = disponibilidades
                .findByUsuarioIdAndEventoId(ana.getId(), ensaio.getId())
                .orElseThrow();
        assertThat(lida.getId()).isEqualTo(salva.getId());
        assertThat(lida.getResposta()).isEqualTo(Resposta.NAO_PODE);
        assertThat(lida.getDataNaResposta()).isEqualTo(MES.atDay(14));
        assertThat(lida.getHorarioNaResposta()).isEqualTo(LocalTime.of(15, 30));
        assertThat(lida.getAtualizadoEm()).isNotNull();
        assertThat(jdbc.queryForObject(
                        "select resposta || '/' || horario_na_resposta_minutos from disponibilidade where id = ?",
                        String.class,
                        salva.getId()))
                .isEqualTo("NAO_PODE/930");
        assertThat(disponibilidades.findByUsuarioIdAndEventoIdIn(ana.getId(), List.of(ensaio.getId())))
                .hasSize(1);
        assertThat(disponibilidades.findByEventoIdIn(List.of(ensaio.getId()))).hasSize(1);
    }

    @Test
    void umaRespostaPorUsuarioEEvento() {
        disponibilidades.saveAndFlush(new Disponibilidade(ana.getId(), ensaio, Resposta.PODE, ana.getId()));

        assertThatThrownBy(() -> disponibilidades.saveAndFlush(
                        new Disponibilidade(ana.getId(), ensaio, Resposta.NAO_PODE, ana.getId())))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("UK_DISPONIBILIDADE");
    }

    @Test
    void oBancoSoAceitaAsRespostasDoModelo() {
        assertThatThrownBy(() -> jdbc.update(
                        "insert into disponibilidade (usuario_id, evento_id, resposta, data_na_resposta,"
                                + " horario_na_resposta_minutos, marcado_por_id) values (?, ?, 'TALVEZ', ?, 930, ?)",
                        ana.getId(),
                        ensaio.getId(),
                        MES.atDay(14),
                        ana.getId()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("CK_DISPONIBILIDADE_RESPOSTA");
    }
}
