package br.igreja.escala.compartilhado.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import br.igreja.escala.TesteDeIntegracao;
import br.igreja.escala.evento.domain.Evento;
import br.igreja.escala.evento.repository.EventoRepository;
import br.igreja.escala.evento.repository.PeriodoRepository;
import br.igreja.escala.evento.service.EventoService;
import br.igreja.escala.evento.service.ModeloEventoService;
import br.igreja.escala.identidade.domain.Usuario;
import br.igreja.escala.identidade.repository.UsuarioRepository;
import br.igreja.escala.identidade.service.UsuarioDetailsService;
import br.igreja.escala.identidade.service.UsuarioService;
import br.igreja.escala.ministerio.domain.Ministerio;
import br.igreja.escala.ministerio.repository.MinisterioRepository;
import br.igreja.escala.ministerio.service.FuncaoService;
import br.igreja.escala.ministerio.service.HabilitacaoService;
import br.igreja.escala.ministerio.service.MembroResumo;
import br.igreja.escala.ministerio.service.MembroService;
import br.igreja.escala.ministerio.service.MinisterioService;
import br.igreja.escala.ministerio.service.NivelService;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

/**
 * Roda o seed do perfil dev no banco dos testes (montado à mão: o bean só existe no perfil dev), para ele não quebrar
 * quando uma regra nova chega, e confere o que o perfil dev promete. Tudo é desfeito no fim do teste.
 */
@TesteDeIntegracao
@Transactional
class SeedDeDesenvolvimentoIT {

    @Autowired
    MinisterioService ministerios;

    @Autowired
    FuncaoService funcoes;

    @Autowired
    NivelService niveis;

    @Autowired
    MembroService membros;

    @Autowired
    HabilitacaoService habilitacoes;

    @Autowired
    ModeloEventoService modelos;

    @Autowired
    EventoService eventos;

    @Autowired
    UsuarioService usuarios;

    @Autowired
    UsuarioDetailsService autenticacao;

    @Autowired
    UsuarioRepository usuarioRepository;

    @Autowired
    MinisterioRepository ministerioRepository;

    @Autowired
    PeriodoRepository periodos;

    @Autowired
    EventoRepository eventoRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Test
    void criaOsMinisteriosOsMembrosEDoisCultosEmCadaDomingo() {
        usuarioRepository.save(Usuario.admin("Admin do Seed", "admin.seed@teste.local", "{noop}x"));
        var seed = new SeedDeDesenvolvimento(
                ministerios,
                funcoes,
                niveis,
                membros,
                habilitacoes,
                modelos,
                eventos,
                usuarios,
                autenticacao,
                "admin.seed@teste.local",
                "senha-dos-membros");

        seed.run(new DefaultApplicationArguments());

        var midia = ministerio("Mídia");
        assertThat(ministerio("Louvor")).isNotNull();
        var daMidia = membros.listar(midia.getId());
        assertThat(daMidia).hasSize(20);
        assertThat(daMidia)
                .filteredOn(MembroResumo::gerente)
                .extracting(MembroResumo::nome)
                .containsExactly("Paula Ribeiro");
        var carla = usuarioRepository.findByEmail("carla.dias@escala.local").orElseThrow();
        var ana = usuarioRepository.findByEmail("ana.souza@escala.local").orElseThrow();
        assertThat(carla.isSenhaProvisoria()).isTrue();
        assertThat(passwordEncoder.matches("senha-dos-membros", ana.getSenhaHash()))
                .isTrue();

        Map<LocalDate, List<Evento>> domingos = eventosDoProximoMes(midia).stream()
                .filter(evento -> evento.getData().getDayOfWeek() == DayOfWeek.SUNDAY)
                .collect(Collectors.groupingBy(Evento::getData));
        assertThat(domingos)
                .isNotEmpty()
                .allSatisfy((data, doDia) -> assertThat(doDia)
                        .extracting(Evento::getNome, Evento::getHorario, Evento::getDuracao)
                        .containsExactlyInAnyOrder(
                                tuple("Culto da manhã", LocalTime.of(9, 30), Duration.ofMinutes(90)),
                                tuple("Culto de domingo", LocalTime.of(18, 0), Duration.ofHours(2))));
        assertThat(eventosDoProximoMes(midia))
                .filteredOn(Evento::isAvulso)
                .singleElement()
                .extracting(Evento::getNome, Evento::getDuracao)
                .containsExactly("Conferência de jovens", Duration.ofHours(4));
    }

    private Ministerio ministerio(String nome) {
        return ministerioRepository.findAllByOrderByNomeAsc().stream()
                .filter(ministerio -> ministerio.getNome().equals(nome))
                .findFirst()
                .orElseThrow();
    }

    private List<Evento> eventosDoProximoMes(Ministerio ministerio) {
        var mes = eventos.proximoMes();
        var periodo = periodos.findByMinisterioIdAndAnoAndMes(ministerio.getId(), mes.getYear(), mes.getMonthValue())
                .orElseThrow();
        return eventoRepository.findByPeriodoIdOrderByDataAscHorarioAsc(periodo.getId());
    }
}
