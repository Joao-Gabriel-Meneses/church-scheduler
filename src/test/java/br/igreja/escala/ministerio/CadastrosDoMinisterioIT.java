package br.igreja.escala.ministerio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.igreja.escala.TesteDeIntegracao;
import br.igreja.escala.identidade.domain.Usuario;
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
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** Mapeamento e constraints das tabelas do ministério no Oracle. Cria os próprios dados. */
@TesteDeIntegracao
@Transactional
class CadastrosDoMinisterioIT {

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
    EntityManager entityManager;

    @Autowired
    JdbcTemplate jdbc;

    private Usuario ana;
    private Ministerio midia;

    @BeforeEach
    void criaUsuarioEMinisterio() {
        ana = usuarios.save(Usuario.membro("Ana Souza", "ana.cadastros@teste.local", "{noop}x"));
        midia = ministerios.save(new Ministerio("Mídia Cadastros", CorDoMinisterio.MINT, Icone.MONITOR));
    }

    @Test
    void gravaELeTudoQueVariaPorMinisterio() {
        var membresia = new Membresia(ana.getId(), midia);
        membresia.tornarGerente();
        membresias.save(membresia);
        var projecao = funcoes.save(new Funcao(midia, "Projeção", Icone.MONITOR, 1, 1));
        var iniciante = niveis.save(new Nivel(midia, "Iniciante", 1));
        habilitacoes.save(new Habilitacao(ana.getId(), projecao, iniciante));
        entityManager.flush();
        entityManager.clear();

        var lida = membresias
                .findByUsuarioIdAndMinisterioId(ana.getId(), midia.getId())
                .orElseThrow();
        assertThat(lida.isGerente()).isTrue();
        assertThat(lida.getMinisterio().getCor()).isEqualTo(CorDoMinisterio.MINT);
        var habilitacao = habilitacoes
                .findByUsuarioIdAndFuncaoId(ana.getId(), projecao.getId())
                .orElseThrow();
        assertThat(habilitacao.getNivel().getNome()).isEqualTo("Iniciante");
        assertThat(habilitacao.getFuncao().getIcone()).isEqualTo(Icone.MONITOR);
        assertThat(membresias.existsByUsuarioIdAndGerenteTrue(ana.getId())).isTrue();
    }

    @Test
    void nomeDoMinisterioEUnicoSemDiferenciarMaiusculas() {
        assertThat(ministerios.existsByNomeIgnoreCase("MÍDIA CADASTROS")).isTrue();
        assertThatThrownBy(() ->
                        ministerios.saveAndFlush(new Ministerio("mídia cadastros", CorDoMinisterio.ROSE, Icone.MUSIC)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void usuarioEntraUmaVezSoEmCadaMinisterio() {
        membresias.saveAndFlush(new Membresia(ana.getId(), midia));

        assertThatThrownBy(() -> membresias.saveAndFlush(new Membresia(ana.getId(), midia)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void umaHabilitacaoPorUsuarioEFuncao() {
        var projecao = funcoes.save(new Funcao(midia, "Projeção", Icone.MONITOR, 1, 1));
        var iniciante = niveis.save(new Nivel(midia, "Iniciante", 1));
        var experiente = niveis.save(new Nivel(midia, "Experiente", 2));
        habilitacoes.saveAndFlush(new Habilitacao(ana.getId(), projecao, iniciante));

        assertThatThrownBy(() -> habilitacoes.saveAndFlush(new Habilitacao(ana.getId(), projecao, experiente)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void nomeDeFuncaoENomeEOrdemDeNivelSaoUnicosNoMinisterio() {
        funcoes.saveAndFlush(new Funcao(midia, "Projeção", Icone.MONITOR, 1, 1));
        niveis.saveAndFlush(new Nivel(midia, "Iniciante", 1));

        assertThat(funcoes.existsByMinisterioIdAndNomeIgnoreCase(midia.getId(), "PROJEÇÃO"))
                .isTrue();
        assertThat(niveis.existsByMinisterioIdAndOrdem(midia.getId(), 1)).isTrue();
        assertThatThrownBy(() -> niveis.saveAndFlush(new Nivel(midia, "Outro", 1)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void bancoRecusaQuantidadeDePessoasIncoerenteMesmoForaDoJava() {
        assertThatThrownBy(() -> jdbc.update(
                        "insert into funcao (ministerio_id, nome, icone, qtd_min, qtd_max) values (?, ?, ?, ?, ?)",
                        midia.getId(),
                        "Incoerente",
                        "MONITOR",
                        2,
                        1))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("CK_FUNCAO_QTD");
    }
}
