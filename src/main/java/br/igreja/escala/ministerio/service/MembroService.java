package br.igreja.escala.ministerio.service;

import br.igreja.escala.compartilhado.NaoEncontradoException;
import br.igreja.escala.compartilhado.RegraVioladaException;
import br.igreja.escala.compartilhado.domain.AcaoAuditada;
import br.igreja.escala.compartilhado.domain.RegistroDeAuditoria;
import br.igreja.escala.compartilhado.service.AuditoriaService;
import br.igreja.escala.identidade.domain.UsuarioAutenticado;
import br.igreja.escala.identidade.service.DadosDaConta;
import br.igreja.escala.identidade.service.NovoUsuario;
import br.igreja.escala.identidade.service.SenhaRecusadaException;
import br.igreja.escala.identidade.service.UsuarioResumo;
import br.igreja.escala.identidade.service.UsuarioService;
import br.igreja.escala.ministerio.domain.Habilitacao;
import br.igreja.escala.ministerio.domain.Membresia;
import br.igreja.escala.ministerio.repository.HabilitacaoRepository;
import br.igreja.escala.ministerio.repository.MembresiaRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Pessoas de um ministério: cadastro com senha provisória, saída, gerentes, dados da conta e redefinição de senha.
 *
 * <p>O gerente mexe só nos membros comuns. Contas de gerente e de admin, e a nomeação de gerentes, ficam com o admin:
 * senão um gerente poderia redefinir a senha (ou trocar o e-mail, que é o login) de alguém com mais acesso que ele e
 * entrar como essa pessoa. A própria conta ninguém muda por aqui: dados e senha ficam em /conta.
 */
@Service
public class MembroService {

    private final MembresiaRepository membresias;
    private final HabilitacaoRepository habilitacoes;
    private final MinisterioService ministerios;
    private final UsuarioService usuarios;
    private final AuditoriaService auditoria;

    MembroService(
            MembresiaRepository membresias,
            HabilitacaoRepository habilitacoes,
            MinisterioService ministerios,
            UsuarioService usuarios,
            AuditoriaService auditoria) {
        this.membresias = membresias;
        this.habilitacoes = habilitacoes;
        this.ministerios = ministerios;
        this.usuarios = usuarios;
        this.auditoria = auditoria;
    }

    /** Membros do ministério em ordem de nome, com as habilitações de cada um. */
    @Transactional(readOnly = true)
    public List<MembroResumo> listar(Long ministerioId) {
        var doMinisterio = membresias.findByMinisterioId(ministerioId);
        var pessoas = usuarios.resumosPorId(
                doMinisterio.stream().map(Membresia::getUsuarioId).toList());
        Map<Long, List<String>> habilitacoesPorPessoa = habilitacoes.findByFuncaoMinisterioId(ministerioId).stream()
                .sorted(Comparator.comparing(
                        habilitacao -> habilitacao.getFuncao().getNome()))
                .collect(Collectors.groupingBy(
                        Habilitacao::getUsuarioId, Collectors.mapping(MembroService::descrever, Collectors.toList())));
        return doMinisterio.stream()
                .map(membresia -> new MembroResumo(
                        pessoas.get(membresia.getUsuarioId()),
                        membresia.isGerente(),
                        habilitacoesPorPessoa.getOrDefault(membresia.getUsuarioId(), List.of())))
                .sorted(Comparator.comparing(MembroResumo::nome, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    /**
     * @throws NaoEncontradoException se a pessoa não é deste ministério
     */
    @Transactional(readOnly = true)
    public MembroResumo buscar(Long ministerioId, Long usuarioId) {
        var membresia = membresiaDe(ministerioId, usuarioId);
        var habilitacoesDaPessoa = habilitacoes.findByUsuarioIdAndFuncaoMinisterioId(usuarioId, ministerioId).stream()
                .sorted(Comparator.comparing(
                        habilitacao -> habilitacao.getFuncao().getNome()))
                .map(MembroService::descrever)
                .toList();
        return new MembroResumo(usuarios.buscar(usuarioId), membresia.isGerente(), habilitacoesDaPessoa);
    }

    /** Se o gerente que está logado pode redefinir a senha deste membro (para mostrar ou não o botão). */
    @Transactional(readOnly = true)
    public boolean podeMexerNaConta(MembroResumo membro, UsuarioAutenticado autor) {
        return autor.isAdmin() || !(membro.pessoa().admin() || membresias.existsByUsuarioIdAndGerenteTrue(membro.id()));
    }

    /**
     * Põe a pessoa no ministério. E-mail novo cria a conta com a senha provisória; e-mail que já tem conta só cria a
     * membresia, sem mudar nada da conta.
     *
     * @throws RegraVioladaException se a pessoa já está no ministério, ou a senha não tem o tamanho certo
     */
    @Transactional
    public ResultadoDoCadastro cadastrar(Long ministerioId, DadosDoMembro dados) {
        var ministerio = ministerios.buscar(ministerioId);
        var existente = usuarios.buscarPorEmail(dados.email());
        if (existente.isPresent()) {
            var pessoa = existente.get();
            if (membresias.existsByUsuarioIdAndMinisterioId(pessoa.id(), ministerioId)) {
                throw new RegraVioladaException("email", pessoa.nome() + " já está neste ministério.");
            }
            membresias.save(new Membresia(pessoa.id(), ministerio));
            return new ResultadoDoCadastro(pessoa, true);
        }
        UsuarioResumo nova;
        try {
            nova = usuarios.criarComSenhaProvisoria(
                    new NovoUsuario(dados.nome(), dados.email(), dados.telefone(), dados.senhaProvisoria()));
        } catch (SenhaRecusadaException recusa) {
            throw new RegraVioladaException(recusa.campo(), recusa.getMessage());
        }
        membresias.save(new Membresia(nova.id(), ministerio));
        return new ResultadoDoCadastro(nova, false);
    }

    /**
     * As sessões abertas do membro são encerradas (UsuarioService). A própria senha não se redefine por aqui: troca-se
     * em /conta/senha, pedindo a atual.
     *
     * @throws RegraVioladaException se é a conta de quem pede, se o membro é gerente ou admin e quem pede não é admin,
     *     ou se a senha não tem o tamanho certo
     */
    @Transactional
    public UsuarioResumo redefinirSenha(Long ministerioId, Long usuarioId, String senha, UsuarioAutenticado autor) {
        var membro = buscar(ministerioId, usuarioId);
        if (autor.getId().equals(usuarioId)) {
            throw RegraVioladaException.geral(
                    "Sua senha não mudou: para trocar a sua própria senha, use Trocar senha no início.");
        }
        if (!podeMexerNaConta(membro, autor)) {
            throw RegraVioladaException.geral("A senha de " + membro.nome()
                    + " não mudou: a senha de um gerente ou administrador só o" + " administrador redefine.");
        }
        try {
            usuarios.redefinirSenhaProvisoria(usuarioId, senha);
        } catch (SenhaRecusadaException recusa) {
            throw RegraVioladaException.geral("A senha de " + membro.nome() + " não mudou. " + recusa.getMessage());
        }
        registrar(
                AcaoAuditada.REDEFINIR_SENHA,
                autor,
                ministerioId,
                membro.pessoa(),
                "Senha provisória de %s redefinida");
        return membro.pessoa();
    }

    /**
     * O membro cuja conta quem pede pode editar (para abrir o formulário).
     *
     * @throws NaoEncontradoException se a pessoa não é deste ministério
     * @throws RegraVioladaException se é a conta de quem pede, ou se o membro é gerente ou admin e quem pede não é admin
     */
    @Transactional(readOnly = true)
    public MembroResumo buscarParaEditar(Long ministerioId, Long usuarioId, UsuarioAutenticado autor) {
        var membro = buscar(ministerioId, usuarioId);
        if (autor.getId().equals(usuarioId)) {
            throw RegraVioladaException.geral("Para mudar os seus dados, use Minha conta no início.");
        }
        if (!podeMexerNaConta(membro, autor)) {
            throw RegraVioladaException.geral("Os dados de " + membro.nome()
                    + " não mudaram: a conta de um gerente ou administrador só o administrador edita.");
        }
        return membro;
    }

    /**
     * Nome, e-mail e telefone de um membro. Registra na auditoria quais campos mudaram, sem os valores.
     *
     * @throws RegraVioladaException como em {@link #buscarParaEditar}, ou no campo {@code email} se o e-mail já é o
     *     login de outra conta
     */
    @Transactional
    public UsuarioResumo editarConta(Long ministerioId, Long usuarioId, DadosDaConta dados, UsuarioAutenticado autor) {
        buscarParaEditar(ministerioId, usuarioId, autor);
        var editada = usuarios.editar(usuarioId, dados);
        if (editada.mudou()) {
            registrar(AcaoAuditada.EDITAR_CONTA, autor, ministerioId, editada.conta(), "%s: " + editada.resumo());
        }
        return editada.conta();
    }

    /**
     * Tira a pessoa do ministério, com as habilitações dela aqui. A conta continua, e as outras membresias também.
     *
     * @throws RegraVioladaException se a pessoa é gerente e quem pede não é admin
     */
    @Transactional
    public UsuarioResumo remover(Long ministerioId, Long usuarioId, UsuarioAutenticado autor) {
        var membresia = membresiaDe(ministerioId, usuarioId);
        var pessoa = usuarios.buscar(usuarioId);
        if (membresia.isGerente() && !autor.isAdmin()) {
            throw RegraVioladaException.geral(
                    pessoa.nome() + " continua no ministério: só o administrador remove um gerente.");
        }
        habilitacoes.deleteAll(habilitacoes.findByUsuarioIdAndFuncaoMinisterioId(usuarioId, ministerioId));
        membresias.delete(membresia);
        registrar(AcaoAuditada.REMOVER_MEMBRO, autor, ministerioId, pessoa, "%s saiu do ministério");
        return pessoa;
    }

    @Transactional
    public UsuarioResumo tornarGerente(Long ministerioId, Long usuarioId, UsuarioAutenticado autor) {
        exigirAdmin(autor);
        membresiaDe(ministerioId, usuarioId).tornarGerente();
        var membro = buscar(ministerioId, usuarioId);
        registrar(
                AcaoAuditada.NOMEAR_GERENTE, autor, ministerioId, membro.pessoa(), "%s agora é gerente do ministério");
        return membro.pessoa();
    }

    @Transactional
    public UsuarioResumo removerGerente(Long ministerioId, Long usuarioId, UsuarioAutenticado autor) {
        exigirAdmin(autor);
        membresiaDe(ministerioId, usuarioId).removerGerente();
        var membro = buscar(ministerioId, usuarioId);
        registrar(
                AcaoAuditada.REMOVER_GERENTE,
                autor,
                ministerioId,
                membro.pessoa(),
                "%s deixou de ser gerente do ministério");
        return membro.pessoa();
    }

    private Membresia membresiaDe(Long ministerioId, Long usuarioId) {
        return membresias
                .findByUsuarioIdAndMinisterioId(usuarioId, ministerioId)
                .orElseThrow(
                        () -> new NaoEncontradoException("Usuário " + usuarioId + " no ministério " + ministerioId));
    }

    /** O controller já exige o perfil; aqui é a garantia para quem chamar o serviço por outro caminho. */
    private static void exigirAdmin(UsuarioAutenticado autor) {
        if (!autor.isAdmin()) {
            throw new AccessDeniedException("Só o administrador nomeia e remove gerentes");
        }
    }

    /** A descrição leva os nomes, para ler a auditoria sem cruzar tabelas: "Ana Souza saiu do ministério (Mídia)." */
    private void registrar(
            AcaoAuditada acao, UsuarioAutenticado autor, Long ministerioId, UsuarioResumo pessoa, String modelo) {
        String descricao = modelo.formatted(pessoa.nome()) + " ("
                + ministerios.buscar(ministerioId).getNome() + ").";
        auditoria.registrar(new RegistroDeAuditoria(acao, autor.getId(), ministerioId, pessoa.id(), descricao));
    }

    private static String descrever(Habilitacao habilitacao) {
        return habilitacao.getFuncao().getNome() + " · "
                + habilitacao.getNivel().getNome();
    }
}
