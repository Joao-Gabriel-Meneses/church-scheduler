package br.igreja.escala.ministerio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.igreja.escala.TesteDeIntegracao;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** Funções e níveis no banco de verdade, com o gerente de um ministério tentando mexer no outro. */
@TesteDeIntegracao
@Transactional
class FuncoesENiveisIT {

    @Autowired
    MockMvc mvc;

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

    private UsuarioAutenticado gerenteDaMidia;
    private Ministerio midia;
    private Funcao projecaoDoLouvor;
    private Nivel solistaDoLouvor;

    @BeforeEach
    void prepara() {
        var gerente = usuarios.save(Usuario.membro("Gerente Funções", "gerente.funcoes@teste.local", "{noop}x"));
        midia = ministerios.save(new Ministerio("Mídia Funções", CorDoMinisterio.MINT, Icone.MONITOR));
        var louvor = ministerios.save(new Ministerio("Louvor Funções", CorDoMinisterio.ROSE, Icone.MUSIC));
        var membresia = new Membresia(gerente.getId(), midia);
        membresia.tornarGerente();
        membresias.save(membresia);
        projecaoDoLouvor = funcoes.save(new Funcao(louvor, "Projeção", Icone.MONITOR, 1, 1));
        solistaDoLouvor = niveis.save(new Nivel(louvor, "Solista", 1));
        gerenteDaMidia = new UsuarioAutenticado(gerente);
    }

    @Test
    void gerenteCriaFuncaoENivelNoProprioMinisterio() throws Exception {
        mvc.perform(post("/ministerios/{m}/funcoes", midia.getId())
                        .with(user(gerenteDaMidia))
                        .with(csrf())
                        .param("nome", "Transmissão")
                        .param("icone", "VIDEO")
                        .param("qtdMin", "1")
                        .param("qtdMax", "1"))
                .andExpect(redirectedUrl("/ministerios/" + midia.getId() + "/funcoes"));
        mvc.perform(post("/ministerios/{m}/funcoes/niveis", midia.getId())
                        .with(user(gerenteDaMidia))
                        .with(csrf())
                        .param("nome", "Iniciante")
                        .param("ordem", "1"))
                .andExpect(redirectedUrl("/ministerios/" + midia.getId() + "/funcoes"));

        assertThat(funcoes.findByMinisterioIdOrderByNomeAsc(midia.getId()))
                .extracting(Funcao::getNome)
                .containsExactly("Transmissão");
        assertThat(niveis.findByMinisterioIdOrderByOrdemAsc(midia.getId()))
                .extracting(Nivel::getNome)
                .containsExactly("Iniciante");
    }

    @Test
    void idDeOutroMinisterioNaRotaDoProprioE404MesmoNoPostDireto() throws Exception {
        long m = midia.getId();
        long f = projecaoDoLouvor.getId();
        long n = solistaDoLouvor.getId();

        mvc.perform(get("/ministerios/{m}/funcoes/{f}", m, f).with(user(gerenteDaMidia)))
                .andExpect(status().isNotFound());
        mvc.perform(post("/ministerios/{m}/funcoes/{f}", m, f)
                        .with(user(gerenteDaMidia))
                        .with(csrf())
                        .param("nome", "Tomada")
                        .param("icone", "MONITOR")
                        .param("qtdMin", "1")
                        .param("qtdMax", "1"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/ministerios/{m}/funcoes/{f}/excluir", m, f)
                        .with(user(gerenteDaMidia))
                        .with(csrf()))
                .andExpect(status().isNotFound());
        mvc.perform(post("/ministerios/{m}/funcoes/niveis/{n}/excluir", m, n)
                        .with(user(gerenteDaMidia))
                        .with(csrf()))
                .andExpect(status().isNotFound());

        assertThat(funcoes.findById(f)).get().extracting(Funcao::getNome).isEqualTo("Projeção");
        assertThat(niveis.findById(n)).isPresent();
    }

    @Test
    void gerenteDeUmMinisterioNaoAbreOutro() throws Exception {
        long louvor = projecaoDoLouvor.getMinisterio().getId();

        mvc.perform(get("/ministerios/{m}/funcoes", louvor).with(user(gerenteDaMidia)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/ministerios/{m}/funcoes/{f}/excluir", louvor, projecaoDoLouvor.getId())
                        .with(user(gerenteDaMidia))
                        .with(csrf()))
                .andExpect(status().isForbidden());
        assertThat(funcoes.findById(projecaoDoLouvor.getId())).isPresent();
    }

    @Test
    void funcaoComMembroHabilitadoNaoEExcluida() throws Exception {
        var projecao = funcoes.save(new Funcao(midia, "Projeção", Icone.MONITOR, 1, 1));
        var iniciante = niveis.save(new Nivel(midia, "Iniciante", 1));
        var ana = usuarios.save(Usuario.membro("Ana Funções", "ana.funcoes@teste.local", "{noop}x"));
        habilitacoes.save(new Habilitacao(ana.getId(), projecao, iniciante));

        mvc.perform(post("/ministerios/{m}/funcoes/{f}/excluir", midia.getId(), projecao.getId())
                        .with(user(gerenteDaMidia))
                        .with(csrf()))
                .andExpect(redirectedUrl("/ministerios/" + midia.getId() + "/funcoes/" + projecao.getId()));

        assertThat(funcoes.findById(projecao.getId())).isPresent();
    }
}
