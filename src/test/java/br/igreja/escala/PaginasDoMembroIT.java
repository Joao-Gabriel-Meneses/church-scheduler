package br.igreja.escala;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.igreja.escala.compartilhado.Datas;
import br.igreja.escala.compartilhado.Fuso;
import br.igreja.escala.escala.domain.Vaga;
import br.igreja.escala.escala.repository.VagaRepository;
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
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Abre as páginas do membro como em produção, sem a transação do teste em volta da requisição (como o
 * PaginasDoGerenteIT): uma associação lazy lida fora do serviço só falha assim. Os dados são gravados de verdade e
 * apagados no {@code @AfterEach}.
 *
 * <p>A Ana é membro da Mídia e está na Projeção do dia 10 do mês que vem (escala publicada) e do dia 10 do mês
 * seguinte (rascunho). O Louvor tem escala publicada, mas ela não é membro dele.
 */
@TesteDeIntegracao
class PaginasDoMembroIT {

    private static final YearMonth PUBLICADO = YearMonth.now(Fuso.SAO_PAULO).plusMonths(1);
    private static final YearMonth RASCUNHO = PUBLICADO.plusMonths(1);

    /** A Ana numa vaga da grade (o nome dela também aparece no menu do usuário). */
    private static final String NA_GRADE = "<span>Ana Membro<span class=\"rt-slot__meta\">";

    @Autowired
    MockMvc mvc;

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

    @Autowired
    VagaRepository vagas;

    private Long midiaId;
    private Long louvorId;
    private UsuarioAutenticado ana;

    @BeforeEach
    void gravaOsDados() {
        transacao.executeWithoutResult(status -> {
            var midia = ministerios.save(new Ministerio("Mídia Membro", CorDoMinisterio.MINT, Icone.MONITOR));
            var louvor = ministerios.save(new Ministerio("Louvor Membro", CorDoMinisterio.ROSE, Icone.MUSIC));
            midiaId = midia.getId();
            louvorId = louvor.getId();
            var daAna = usuarios.save(Usuario.membro("Ana Membro", "ana.membro@teste.local", "{noop}x"));
            var bia = usuarios.save(Usuario.membro("Bia Membro", "bia.membro@teste.local", "{noop}x"));
            membresias.save(new Membresia(daAna.getId(), midia));
            membresias.save(new Membresia(bia.getId(), louvor));
            var projecao = funcoes.save(new Funcao(midia, "Projeção", Icone.MONITOR, 1, 1));
            var vocal = funcoes.save(new Funcao(louvor, "Vocal", Icone.MIC, 1, 1));
            habilitacoes.save(new Habilitacao(daAna.getId(), projecao, niveis.save(new Nivel(midia, "Experiente", 1))));
            habilitacoes.save(new Habilitacao(bia.getId(), vocal, niveis.save(new Nivel(louvor, "Experiente", 1))));

            var publicado = new Periodo(midiaId, PUBLICADO);
            publicado.publicarEscala();
            periodos.save(publicado);
            var rascunho = periodos.save(new Periodo(midiaId, RASCUNHO));
            var doLouvor = new Periodo(louvorId, PUBLICADO);
            doLouvor.publicarEscala();
            periodos.save(doLouvor);
            escalar(daAna, projecao, eventos.save(culto(publicado, "Culto publicado", PUBLICADO)));
            escalar(daAna, projecao, eventos.save(culto(rascunho, "Culto em rascunho", RASCUNHO)));
            escalar(bia, vocal, eventos.save(culto(doLouvor, "Ensaio do louvor", PUBLICADO)));
            ana = new UsuarioAutenticado(daAna);
        });
    }

    @AfterEach
    void apagaOsDados() {
        transacao.executeWithoutResult(status -> {
            Object[] ids = {midiaId, louvorId};
            String doMinisterio = "(select id from evento where ministerio_id in (?, ?))";
            jdbc.update("delete from vaga where evento_id in " + doMinisterio, ids);
            jdbc.update("delete from evento_funcao where evento_id in " + doMinisterio, ids);
            jdbc.update("delete from evento where ministerio_id in (?, ?)", ids);
            jdbc.update("delete from periodo where ministerio_id in (?, ?)", ids);
            jdbc.update(
                    "delete from habilitacao where funcao_id in (select id from funcao where ministerio_id in (?, ?))",
                    ids);
            jdbc.update("delete from funcao where ministerio_id in (?, ?)", ids);
            jdbc.update("delete from nivel where ministerio_id in (?, ?)", ids);
            jdbc.update("delete from regra where ministerio_id in (?, ?)", ids);
            jdbc.update("delete from membresia where ministerio_id in (?, ?)", ids);
            jdbc.update("delete from ministerio where id in (?, ?)", ids);
            jdbc.update("delete from usuario where email like '%.membro@teste.local'");
        });
    }

    @Test
    void inicioMostraSoAsEscalasPublicadasComOMinisterio() throws Exception {
        mvc.perform(get("/").with(user(ana)))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString(Datas.dataCurta(PUBLICADO.atDay(10)))))
                .andExpect(content().string(Matchers.containsString("Projeção · Culto publicado")))
                .andExpect(content().string(Matchers.containsString("Mídia Membro")))
                .andExpect(content().string(Matchers.containsString("Confirmar desistência")))
                .andExpect(content().string(Matchers.containsString("/desistir\"")))
                .andExpect(content().string(Matchers.not(Matchers.containsString("Culto em rascunho"))))
                .andExpect(content().string(Matchers.not(Matchers.containsString("Ensaio do louvor"))));
    }

    @Test
    void escalaPublicadaDoMinisterioAbreComAGrade() throws Exception {
        mvc.perform(get("/escalas/{m}", midiaId)
                        .param("mes", PUBLICADO.toString())
                        .with(user(ana)))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("rt-sched")))
                .andExpect(content().string(Matchers.containsString(NA_GRADE)))
                .andExpect(content().string(Matchers.containsString("Culto publicado")));
    }

    @Test
    void rascunhoNaoApareceNemPorUrlDireta() throws Exception {
        mvc.perform(get("/escalas/{m}", midiaId)
                        .param("mes", RASCUNHO.toString())
                        .with(user(ana)))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("ainda não foi publicada")))
                .andExpect(content().string(Matchers.not(Matchers.containsString("Culto em rascunho"))))
                .andExpect(content().string(Matchers.not(Matchers.containsString(NA_GRADE))))
                .andExpect(content().string(Matchers.not(Matchers.containsString("rt-sched"))));
    }

    @Test
    void escalaDeMinisterioDeQueNaoEMembroE404() throws Exception {
        mvc.perform(get("/escalas/{m}", louvorId)
                        .param("mes", PUBLICADO.toString())
                        .with(user(ana)))
                .andExpect(status().isNotFound());
    }

    private static Evento culto(Periodo periodo, String nome, YearMonth mes) {
        return Evento.avulso(periodo, nome, mes.atDay(10), LocalTime.of(18, 0), Duration.ofHours(2));
    }

    private void escalar(Usuario pessoa, Funcao funcao, Evento evento) {
        var vaga = new Vaga(evento.getId(), funcao.getId(), 1);
        vaga.escalar(pessoa.getId());
        vagas.save(vaga);
    }
}
