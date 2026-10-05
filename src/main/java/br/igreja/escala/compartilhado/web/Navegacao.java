package br.igreja.escala.compartilhado.web;

import br.igreja.escala.identidade.domain.Perfil;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.security.core.GrantedAuthority;

/**
 * Navegação principal: NavPills no desktop e barra inferior no celular (templates/componentes/navegacao.html), e a
 * SideRail com os ministérios que o usuário gerencia.
 *
 * <p>As abas são as mesmas em todas as páginas: Início, Disponibilidade e, para quem gerencia algum ministério (ou é
 * admin), o menu "Gerenciar" com as páginas do gerente. Só muda qual está atual. Dentro de um ministério
 * ({@code /ministerios/{id}/…}), os itens do menu levam às páginas daquele ministério; fora, às do primeiro gerenciado.
 */
public final class Navegacao {

    /** Na URL de uma entrada, é trocado pelo id do ministério. */
    static final String MINISTERIO = "{ministerio}";

    /** Seção padrão ao entrar num ministério pela SideRail ou pelo menu "Gerenciar". */
    static final String SECAO_INICIAL = "escalas";

    private static final Pattern AREA_DO_MINISTERIO = Pattern.compile("^/ministerios/(\\d+)(?:/([^/]+))?(?:/.*)?$");

    private static final String NO_MINISTERIO = "/ministerios/" + MINISTERIO + "/";

    /** Entradas na ordem em que aparecem. O ícone precisa estar em src/main/frontend/icones.json. */
    static final Navegacao PRINCIPAL = new Navegacao(List.of(
            Entrada.de("Início", "Início", "house", "/", Perfil.MEMBRO),
            Entrada.de("Disponibilidade", "Disponib.", "list-checks", "/disponibilidade", Perfil.MEMBRO),
            Entrada.menu(
                    "Gerenciar",
                    "Gerenciar",
                    "layout-dashboard",
                    List.of(
                            Entrada.de(
                                    "Escalas",
                                    "Escalas",
                                    "layout-dashboard",
                                    NO_MINISTERIO + SECAO_INICIAL,
                                    Perfil.MEMBRO),
                            Entrada.de("Eventos", "Eventos", "calendar", NO_MINISTERIO + "eventos", Perfil.MEMBRO),
                            Entrada.de(
                                    "Disponibilidade",
                                    "Disponib.",
                                    "list-checks",
                                    NO_MINISTERIO + "disponibilidade",
                                    Perfil.MEMBRO),
                            Entrada.de("Membros", "Membros", "users", NO_MINISTERIO + "membros", Perfil.MEMBRO),
                            Entrada.de("Funções", "Funções", "layers", NO_MINISTERIO + "funcoes", Perfil.MEMBRO),
                            Entrada.de("Regras", "Regras", "settings-2", NO_MINISTERIO + "regras", Perfil.MEMBRO),
                            Entrada.de("Ministérios", "Ministérios", "church", "/admin/ministerios", Perfil.ADMIN)
                                    .comSeparador()))));

    private final List<Entrada> entradas;

    Navegacao(List<Entrada> entradas) {
        this.entradas = List.copyOf(entradas);
    }

    /**
     * Abas que quem tem essas autoridades vê, com a da página marcada. O conjunto não depende do caminho.
     *
     * @param caminho caminho da requisição sem o contexto ("/ministerios/3/membros")
     * @param gerenciados ministérios que o usuário gerencia, em ordem
     */
    List<ItemDeNavegacao> itens(
            Collection<? extends GrantedAuthority> autoridades,
            String caminho,
            List<MinisterioNaNavegacao> gerenciados) {
        Set<String> nomes =
                autoridades.stream().map(GrantedAuthority::getAuthority).collect(Collectors.toSet());
        var local = Local.de(caminho);
        Optional<Long> ministerio =
                local.ministerioId().or(() -> gerenciados.stream().findFirst().map(MinisterioNaNavegacao::id));
        return itens(entradas, nomes, ministerio, caminho);
    }

    private static List<ItemDeNavegacao> itens(
            List<Entrada> entradas, Set<String> nomes, Optional<Long> ministerio, String caminho) {
        return entradas.stream()
                .filter(entrada -> nomes.contains(entrada.perfil().authority()))
                .filter(entrada ->
                        entrada.url() == null || !entrada.url().contains(MINISTERIO) || ministerio.isPresent())
                .map(entrada -> {
                    if (entrada.filhos().isEmpty()) {
                        return item(entrada, ministerio, caminho);
                    }
                    var filhos = itens(entrada.filhos(), nomes, ministerio, caminho);
                    return filhos.isEmpty()
                            ? null
                            : ItemDeNavegacao.menu(entrada.rotulo(), entrada.rotuloCurto(), entrada.icone(), filhos);
                })
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * SideRail: os ministérios gerenciados, só dentro da área de um ministério. Cada item leva à mesma seção no outro
     * ministério (de "Membros" da Mídia para "Membros" do Louvor).
     */
    static List<ItemDeNavegacao> ministerios(String caminho, List<MinisterioNaNavegacao> gerenciados) {
        var local = Local.de(caminho);
        if (local.ministerioId().isEmpty()) {
            return List.of();
        }
        String secao = local.secao().orElse(SECAO_INICIAL);
        return gerenciados.stream()
                .map(ministerio -> new ItemDeNavegacao(
                        ministerio.nome(),
                        ministerio.nome(),
                        ministerio.icone(),
                        "/ministerios/" + ministerio.id() + "/" + secao,
                        local.ministerioId().filter(ministerio.id()::equals).isPresent()))
                .toList();
    }

    private static ItemDeNavegacao item(Entrada entrada, Optional<Long> ministerio, String caminho) {
        String url = ministerio
                .map(id -> entrada.url().replace(MINISTERIO, id.toString()))
                .orElse(entrada.url());
        return new ItemDeNavegacao(
                entrada.rotulo(),
                entrada.rotuloCurto(),
                entrada.icone(),
                url,
                estaEm(caminho, url),
                entrada.separada(),
                List.of());
    }

    /** "/" só vale para o início; as outras seções valem também para as subpáginas ("/membros/3"). */
    private static boolean estaEm(String caminho, String url) {
        if (url.equals("/")) {
            return caminho.equals("/");
        }
        return caminho.equals(url) || caminho.startsWith(url + "/");
    }

    /**
     * Uma aba ou um item do menu. Com {@code filhos}, é um menu: aparece se algum filho aparecer e não tem URL própria.
     *
     * @param separada no menu, abre um grupo novo (ex.: Ministérios, que é do admin e vale para todos)
     */
    record Entrada(
            String rotulo,
            String rotuloCurto,
            String icone,
            String url,
            Perfil perfil,
            boolean separada,
            List<Entrada> filhos) {

        static Entrada de(String rotulo, String rotuloCurto, String icone, String url, Perfil perfil) {
            return new Entrada(rotulo, rotuloCurto, icone, url, perfil, false, List.of());
        }

        static Entrada menu(String rotulo, String rotuloCurto, String icone, List<Entrada> filhos) {
            return new Entrada(rotulo, rotuloCurto, icone, null, Perfil.MEMBRO, false, List.copyOf(filhos));
        }

        Entrada comSeparador() {
            return new Entrada(rotulo, rotuloCurto, icone, url, perfil, true, filhos);
        }
    }

    /** Se o caminho está num ministério, e em qual seção dele. */
    record Local(Optional<Long> ministerioId, Optional<String> secao) {

        static Local de(String caminho) {
            var area = AREA_DO_MINISTERIO.matcher(caminho);
            if (!area.matches()) {
                return new Local(Optional.empty(), Optional.empty());
            }
            return new Local(Optional.of(Long.valueOf(area.group(1))), Optional.ofNullable(area.group(2)));
        }
    }
}
