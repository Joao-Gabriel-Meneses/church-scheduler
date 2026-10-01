package br.igreja.escala.disponibilidade;

import static br.igreja.escala.evento.ExemplosDeEvento.DUAS_HORAS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.igreja.escala.TesteDeIntegracao;
import br.igreja.escala.compartilhado.Fuso;
import br.igreja.escala.compartilhado.RegraVioladaException;
import br.igreja.escala.disponibilidade.domain.Resposta;
import br.igreja.escala.disponibilidade.repository.DisponibilidadeRepository;
import br.igreja.escala.disponibilidade.service.DisponibilidadeService;
import br.igreja.escala.evento.domain.Evento;
import br.igreja.escala.evento.domain.Periodo;
import br.igreja.escala.evento.repository.EventoRepository;
import br.igreja.escala.evento.repository.PeriodoRepository;
import br.igreja.escala.identidade.domain.Usuario;
import br.igreja.escala.identidade.domain.UsuarioAutenticado;
import br.igreja.escala.identidade.repository.UsuarioRepository;
import br.igreja.escala.ministerio.domain.CorDoMinisterio;
import br.igreja.escala.ministerio.domain.Funcao;
import br.igreja.escala.ministerio.domain.Habilitacao;
import br.igreja.escala.ministerio.domain.Icone;
import br.igreja.escala.ministerio.domain.Membresia;
import br.igreja.escala.ministerio.domain.Ministerio;
import br.igreja.escala.ministerio.domain.Nivel;
import br.igreja.escala.ministerio.repository.FuncaoRepository;
import br.igreja.escala.ministerio.repository.HabilitacaoRepository;
import br.igreja.escala.ministerio.repository.MembresiaRepository;
import br.igreja.escala.ministerio.repository.MinisterioRepository;
import br.igreja.escala.ministerio.repository.NivelRepository;
import java.time.Duration;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Trava e respostas ao mesmo tempo, em transações de verdade (sem {@code @Transactional} no teste): a gravação lê a
 * trava com a linha do período bloqueada, então espera quem está travando e vê a trava. Os dados são gravados de
 * verdade e apagados no {@code @AfterEach}.
 */
@TesteDeIntegracao
class DisponibilidadeConcorrenciaIT {

    private static final YearMonth MES = YearMonth.now(Fuso.SAO_PAULO).plusMonths(2);

    /** Quanto a transação que trava segura o bloqueio antes do commit. */
    private static final Duration SEGURANDO_A_TRAVA = Duration.ofMillis(1500);

    @Autowired
    DisponibilidadeService servico;

    @Autowired
    DisponibilidadeRepository disponibilidades;

    @Autowired
    TransactionTemplate transacao;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    UsuarioRepository usuarios;

    @Autowired
    MinisterioRepository ministerios;

    @Autowired
    MembresiaRepository membresias;

    @Autowired
    FuncaoRepository funcoes;

    @Autowired
    NivelRepository niveis;

    @Autowired
    HabilitacaoRepository habilitacoes;

    @Autowired
    PeriodoRepository periodos;

    @Autowired
    EventoRepository eventos;

    private final ExecutorService paralelo = Executors.newFixedThreadPool(2);

    private Long ministerioId;
    private Long anaId;
    private UsuarioAutenticado gerente;
    private final List<Long> eventoIds = new ArrayList<>();

    @BeforeEach
    void gravaOsDados() {
        transacao.executeWithoutResult(status -> {
            var midia = ministerios.save(new Ministerio("Mídia Concorrência", CorDoMinisterio.MINT, Icone.MONITOR));
            ministerioId = midia.getId();
            var ana = usuarios.save(Usuario.membro("Ana Concorrência", "ana.concorrencia@teste.local", "{noop}x"));
            anaId = ana.getId();
            var paula =
                    usuarios.save(Usuario.membro("Paula Concorrência", "paula.concorrencia@teste.local", "{noop}x"));
            var membresiaDaPaula = new Membresia(paula.getId(), midia);
            membresiaDaPaula.tornarGerente();
            membresias.save(membresiaDaPaula);
            membresias.save(new Membresia(anaId, midia));
            var projecao = funcoes.save(new Funcao(midia, "Projeção", Icone.MONITOR, 1, 1));
            habilitacoes.save(new Habilitacao(anaId, projecao, niveis.save(new Nivel(midia, "Iniciante", 1))));
            var periodo = periodos.save(new Periodo(ministerioId, MES));
            for (int dia = 10; dia < 15; dia++) {
                eventoIds.add(eventos.save(Evento.avulso(
                                periodo, "Ensaio " + dia, MES.atDay(dia), LocalTime.of(19, 0), DUAS_HORAS))
                        .getId());
            }
            gerente = new UsuarioAutenticado(paula);
        });
    }

    @AfterEach
    void apagaOsDados() {
        paralelo.shutdownNow();
        transacao.executeWithoutResult(status -> {
            jdbc.update(
                    "delete from disponibilidade where evento_id in (select id from evento where ministerio_id = ?)",
                    ministerioId);
            jdbc.update("delete from auditoria where ministerio_id = ?", ministerioId);
            jdbc.update("delete from evento where ministerio_id = ?", ministerioId);
            jdbc.update("delete from periodo where ministerio_id = ?", ministerioId);
            jdbc.update(
                    "delete from habilitacao where funcao_id in (select id from funcao where ministerio_id = ?)",
                    ministerioId);
            jdbc.update("delete from funcao where ministerio_id = ?", ministerioId);
            jdbc.update("delete from nivel where ministerio_id = ?", ministerioId);
            jdbc.update("delete from membresia where ministerio_id = ?", ministerioId);
            jdbc.update("delete from ministerio where id = ?", ministerioId);
            jdbc.update("delete from usuario where email like '%.concorrencia@teste.local'");
        });
    }

    @Test
    void respostaQueChegaDuranteATravaEsperaEERecusada() throws Exception {
        var travou = new CountDownLatch(1);
        Future<?> travando = paralelo.submit(() -> transacao.executeWithoutResult(status -> {
            servico.travar(ministerioId, MES, gerente);
            travou.countDown();
            dormir(SEGURANDO_A_TRAVA);
        }));
        assertThat(travou.await(10, TimeUnit.SECONDS)).isTrue();

        long inicio = System.nanoTime();
        assertThatThrownBy(() -> servico.marcar(anaId, ministerioId, eventoIds.get(0), Resposta.PODE))
                .as("sem o bloqueio, a resposta leria a trava ainda aberta e entraria")
                .isInstanceOf(RegraVioladaException.class)
                .hasMessageContaining("o gerente travou a disponibilidade");
        Duration esperou = Duration.ofNanos(System.nanoTime() - inicio);
        travando.get(10, TimeUnit.SECONDS);

        assertThat(esperou).as("esperou o commit da trava").isGreaterThan(SEGURANDO_A_TRAVA.dividedBy(2));
        assertThat(disponibilidades.findByEventoIdIn(eventoIds)).isEmpty();
    }

    @Test
    void doisToquesIguaisAoMesmoTempoDeixamUmaLinhaSemErro() throws Exception {
        for (Long eventoId : eventoIds) {
            var largada = new CyclicBarrier(2);
            List<Future<Boolean>> toques = new ArrayList<>();
            for (int toque = 0; toque < 2; toque++) {
                toques.add(paralelo.submit(() -> {
                    largada.await(10, TimeUnit.SECONDS);
                    return servico.marcar(anaId, ministerioId, eventoId, Resposta.PODE);
                }));
            }
            var mudou = new ArrayList<Boolean>();
            for (var toque : toques) {
                mudou.add(toque.get(10, TimeUnit.SECONDS));
            }

            assertThat(mudou).as("um grava e o outro encontra a linha gravada").containsExactlyInAnyOrder(true, false);
        }

        assertThat(disponibilidades.findByEventoIdIn(eventoIds))
                .hasSize(eventoIds.size())
                .allSatisfy(resposta -> assertThat(resposta.getResposta()).isEqualTo(Resposta.PODE));
    }

    private static void dormir(Duration tempo) {
        try {
            Thread.sleep(tempo);
        } catch (InterruptedException interrompido) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrompido);
        }
    }
}
