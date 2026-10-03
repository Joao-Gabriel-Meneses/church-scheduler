package br.igreja.escala.compartilhado.config;

import br.igreja.escala.disponibilidade.domain.Resposta;
import br.igreja.escala.disponibilidade.service.DisponibilidadeService;
import br.igreja.escala.escala.service.RegraService;
import br.igreja.escala.evento.service.DadosDoEvento;
import br.igreja.escala.evento.service.DadosDoModelo;
import br.igreja.escala.evento.service.EventoService;
import br.igreja.escala.evento.service.ModeloEventoService;
import br.igreja.escala.identidade.domain.UsuarioAutenticado;
import br.igreja.escala.identidade.service.UsuarioDetailsService;
import br.igreja.escala.identidade.service.UsuarioResumo;
import br.igreja.escala.identidade.service.UsuarioService;
import br.igreja.escala.ministerio.domain.CorDoMinisterio;
import br.igreja.escala.ministerio.domain.Funcao;
import br.igreja.escala.ministerio.domain.Icone;
import br.igreja.escala.ministerio.domain.Nivel;
import br.igreja.escala.ministerio.service.DadosDaFuncao;
import br.igreja.escala.ministerio.service.DadosDoMembro;
import br.igreja.escala.ministerio.service.DadosDoMinisterio;
import br.igreja.escala.ministerio.service.DadosDoNivel;
import br.igreja.escala.ministerio.service.FuncaoService;
import br.igreja.escala.ministerio.service.HabilitacaoService;
import br.igreja.escala.ministerio.service.MembroService;
import br.igreja.escala.ministerio.service.MinisterioResumo;
import br.igreja.escala.ministerio.service.MinisterioService;
import br.igreja.escala.ministerio.service.NivelService;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Dados de exemplo só no perfil dev: Mídia (com 20 membros, funções, níveis, habilitações, modelos e os eventos deste
 * mês e do próximo, com dois cultos em cada domingo) e Louvor (Vocal para a Ana, a Paula e a Priscila, com um ensaio
 * aos sábados), para a SideRail e a disponibilidade por ministério aparecerem. A Mídia já chega com respostas nos dois
 * meses, com a disponibilidade deste mês travada (pronta para gerar a escala) e com no máximo um Iniciante por evento,
 * configurado como o gerente faria na página de regras. Roda depois do admin inicial e só se a Mídia ainda não existe.
 * Usa só os serviços públicos dos módulos, como uma pessoa faria pelas telas. O SeedDeDesenvolvimentoIT roda o seed no
 * banco dos testes.
 */
@Component
@Profile("dev")
@Order(10)
class SeedDeDesenvolvimento implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SeedDeDesenvolvimento.class);

    private static final String MIDIA = "Mídia";
    private static final String SENHA_PROVISORIA = "provisoria-dev";

    /** Nome, e-mail e o nível em Projeção e Transmissão (null = sem habilitação). */
    private record Pessoa(String nome, String email, String projecao, String transmissao) {}

    private static final List<Pessoa> PESSOAS = List.of(
            new Pessoa("Paula Ribeiro", "paula.ribeiro@escala.local", "Experiente", "Experiente"),
            new Pessoa("Ana Souza", "ana.souza@escala.local", "Experiente", "Experiente"),
            new Pessoa("Lucas Lima", "lucas.lima@escala.local", "Iniciante", null),
            new Pessoa("Carla Dias", "carla.dias@escala.local", "Iniciante", "Experiente"),
            new Pessoa("Bruno Alves", "bruno.alves@escala.local", null, "Experiente"),
            new Pessoa("Beatriz Rocha", "beatriz.rocha@escala.local", "Experiente", "Iniciante"),
            new Pessoa("Diego Martins", "diego.martins@escala.local", "Iniciante", "Iniciante"),
            new Pessoa("Elisa Costa", "elisa.costa@escala.local", "Experiente", null),
            new Pessoa("Felipe Nunes", "felipe.nunes@escala.local", null, "Iniciante"),
            new Pessoa("Gabriela Melo", "gabriela.melo@escala.local", "Experiente", "Experiente"),
            new Pessoa("Henrique Prado", "henrique.prado@escala.local", "Iniciante", null),
            new Pessoa("Isabela Freitas", "isabela.freitas@escala.local", null, "Experiente"),
            new Pessoa("João Pedro Santos", "joao.santos@escala.local", "Experiente", "Iniciante"),
            new Pessoa("Larissa Teixeira", "larissa.teixeira@escala.local", "Iniciante", "Iniciante"),
            new Pessoa("Marcos Vieira", "marcos.vieira@escala.local", "Experiente", null),
            new Pessoa("Natália Gomes", "natalia.gomes@escala.local", null, null),
            new Pessoa("Otávio Barros", "otavio.barros@escala.local", "Iniciante", "Experiente"),
            new Pessoa("Priscila Cardoso", "priscila.cardoso@escala.local", "Experiente", "Iniciante"),
            new Pessoa("Rafael Moreira", "rafael.moreira@escala.local", null, "Iniciante"),
            new Pessoa("Sofia Araújo", "sofia.araujo@escala.local", "Iniciante", null));

    private final MinisterioService ministerios;
    private final FuncaoService funcoes;
    private final NivelService niveis;
    private final MembroService membros;
    private final HabilitacaoService habilitacoes;
    private final ModeloEventoService modelos;
    private final EventoService eventos;
    private final UsuarioService usuarios;
    private final UsuarioDetailsService autenticacao;
    private final DisponibilidadeService disponibilidades;
    private final RegraService regras;
    private final String emailDoAdmin;
    private final String senhaDosMembros;

    SeedDeDesenvolvimento(
            MinisterioService ministerios,
            FuncaoService funcoes,
            NivelService niveis,
            MembroService membros,
            HabilitacaoService habilitacoes,
            ModeloEventoService modelos,
            EventoService eventos,
            UsuarioService usuarios,
            UsuarioDetailsService autenticacao,
            DisponibilidadeService disponibilidades,
            RegraService regras,
            @Value("${escala.admin.email}") String emailDoAdmin,
            @Value("${escala.dev.senha-dos-membros}") String senhaDosMembros) {
        this.ministerios = ministerios;
        this.funcoes = funcoes;
        this.niveis = niveis;
        this.membros = membros;
        this.habilitacoes = habilitacoes;
        this.modelos = modelos;
        this.eventos = eventos;
        this.usuarios = usuarios;
        this.autenticacao = autenticacao;
        this.disponibilidades = disponibilidades;
        this.regras = regras;
        this.emailDoAdmin = emailDoAdmin;
        this.senhaDosMembros = senhaDosMembros;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (ministerios.resumos().stream().map(MinisterioResumo::nome).anyMatch(MIDIA::equals)) {
            return;
        }
        var admin = autenticacao.loadUserByUsername(emailDoAdmin);
        var midia = ministerios
                .criar(new DadosDoMinisterio(MIDIA, CorDoMinisterio.MINT, Icone.MONITOR))
                .getId();
        var louvor = ministerios
                .criar(new DadosDoMinisterio("Louvor", CorDoMinisterio.ROSE, Icone.MUSIC))
                .getId();

        var projecao = funcoes.criar(midia, new DadosDaFuncao("Projeção", Icone.MONITOR, 1, 1));
        var transmissao = funcoes.criar(midia, new DadosDaFuncao("Transmissão", Icone.VIDEO, 1, 1));
        Map<String, Nivel> doMinisterio = Map.of(
                "Iniciante", niveis.criar(midia, new DadosDoNivel("Iniciante", 1)),
                "Experiente", niveis.criar(midia, new DadosDoNivel("Experiente", 2)));
        var vocal = funcoes.criar(louvor, new DadosDaFuncao("Vocal", Icone.MIC, 1, 3));
        var vozExperiente = niveis.criar(louvor, new DadosDoNivel("Experiente", 1));

        for (Pessoa pessoa : PESSOAS) {
            var id = membros.cadastrar(midia, new DadosDoMembro(pessoa.nome(), pessoa.email(), null, SENHA_PROVISORIA))
                    .membro()
                    .id();
            if (!pessoa.nome().equals("Carla Dias")) {
                usuarios.trocarSenha(id, null, senhaDosMembros);
            }
            habilitacoes.definir(midia, id, niveisDe(pessoa, projecao, transmissao, doMinisterio));
            if (pessoa.nome().startsWith("A") || pessoa.nome().startsWith("P")) {
                membros.cadastrar(louvor, new DadosDoMembro(pessoa.nome(), pessoa.email(), null, SENHA_PROVISORIA));
                habilitacoes.definir(louvor, id, Map.of(vocal.getId(), vozExperiente.getId()));
            }
        }
        var paula = usuarios.buscarPorEmail("paula.ribeiro@escala.local").orElseThrow();
        membros.tornarGerente(midia, paula.id(), admin);
        regras.alterarMaximoPorNivel(midia, true, doMinisterio.get("Iniciante").getId(), 1, admin);

        // Dois cultos no mesmo domingo: a escala (Fase 3) não pode pôr a mesma pessoa nos dois se eles se sobrepuserem,
        // e o limite do mês conta cada um.
        modelos.criar(midia, new DadosDoModelo("Culto da manhã", DayOfWeek.SUNDAY, LocalTime.of(9, 30), 90, true));
        modelos.criar(midia, new DadosDoModelo("Culto de domingo", DayOfWeek.SUNDAY, LocalTime.of(18, 0), 120, true));
        modelos.criar(midia, new DadosDoModelo("Culto de quinta", DayOfWeek.THURSDAY, LocalTime.of(19, 30), 120, true));
        var proximo = eventos.proximoMes();
        eventos.gerarDoMes(midia, proximo.minusMonths(1));
        eventos.gerarDoMes(midia, proximo);
        eventos.criarAvulso(
                midia, new DadosDoEvento("Conferência de jovens", terceiroSabado(proximo), LocalTime.of(15, 0), 240));
        modelos.criar(louvor, new DadosDoModelo("Ensaio do louvor", DayOfWeek.SATURDAY, LocalTime.of(16, 0), 90, true));
        eventos.gerarDoMes(louvor, proximo.minusMonths(1));
        eventos.gerarDoMes(louvor, proximo);

        var gerente = autenticacao.loadUserByUsername(paula.email());
        responder(midia, proximo.minusMonths(1));
        disponibilidades.travar(midia, proximo.minusMonths(1), admin);
        responder(midia, proximo);
        exemplosDaAna(midia, proximo, gerente);
        log.info(
                "Seed de desenvolvimento criado: Mídia e Louvor, {} membros. Gerente: paula.ribeiro@escala.local",
                PESSOAS.size());
    }

    /**
     * Respostas de exemplo da Mídia no mês: doze pessoas respondem tudo, três respondem metade e o resto não responde.
     * A Ana fica de fora (exemplosDaAna).
     */
    private void responder(Long midia, YearMonth mes) {
        var doMes = eventos.porVirDoMes(midia, mes);
        var queServem = membros.queServem(midia);
        for (int i = 0; i < queServem.size(); i++) {
            UsuarioResumo pessoa = queServem.get(i);
            int quantos = i < 12 ? doMes.size() : (i < 15 ? doMes.size() / 2 : 0);
            for (int j = 0; j < quantos && !pessoa.nome().equals("Ana Souza"); j++) {
                var resposta = (i + j) % 3 == 0 ? Resposta.NAO_PODE : Resposta.PODE;
                disponibilidades.marcar(pessoa.id(), midia, doMes.get(j).getId(), resposta);
            }
        }
    }

    /**
     * A Ana responde só os três primeiros eventos do mês, o segundo marcado pela Paula em nome dela, e o horário do
     * terceiro muda depois da resposta, para a tela dela mostrar o "Marcado por" e o aviso.
     */
    private void exemplosDaAna(Long midia, YearMonth mes, UsuarioAutenticado paula) {
        var doMes = eventos.porVirDoMes(midia, mes);
        if (doMes.size() < 3) {
            return;
        }
        var ana = usuarios.buscarPorEmail("ana.souza@escala.local").orElseThrow();
        disponibilidades.marcar(ana.id(), midia, doMes.get(0).getId(), Resposta.PODE);
        disponibilidades.marcarPeloGerente(midia, ana.id(), doMes.get(1).getId(), Resposta.NAO_PODE, paula);
        disponibilidades.marcar(ana.id(), midia, doMes.get(2).getId(), Resposta.PODE);
        var mudou = doMes.get(2);
        eventos.alterar(
                midia,
                mudou.getId(),
                new DadosDoEvento(
                        mudou.getNome(), mudou.getData(), mudou.getHorario().plusMinutes(30), (int)
                                mudou.getDuracao().toMinutes()));
    }

    private static Map<Long, Long> niveisDe(
            Pessoa pessoa, Funcao projecao, Funcao transmissao, Map<String, Nivel> niveis) {
        var porFuncao = new HashMap<Long, Long>();
        if (pessoa.projecao() != null) {
            porFuncao.put(projecao.getId(), niveis.get(pessoa.projecao()).getId());
        }
        if (pessoa.transmissao() != null) {
            porFuncao.put(transmissao.getId(), niveis.get(pessoa.transmissao()).getId());
        }
        return porFuncao;
    }

    private static LocalDate terceiroSabado(YearMonth mes) {
        return mes.atDay(1).with(TemporalAdjusters.dayOfWeekInMonth(3, DayOfWeek.SATURDAY));
    }
}
