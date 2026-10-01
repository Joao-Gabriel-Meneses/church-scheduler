package br.igreja.escala.compartilhado.web;

import br.igreja.escala.identidade.domain.Perfil;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.security.core.GrantedAuthority;

/**
 * Navegação principal: NavPills no desktop e barra inferior no celular (templates/componentes/navegacao.html), e a
 * SideRail com os ministérios que o usuário gerencia.
 *
 * <p>São duas áreas. A do membro (início, conta, páginas do admin) e a de um ministério ({@code /ministerios/{id}/…}),
 * com as páginas do gerente daquele ministério. Na área do membro, quem gerencia algum ministério ganha o atalho
 * "Gerenciar", que leva ao primeiro deles.
 */
public final class Navegacao {

    /** Na URL de uma entrada, é trocado pelo id do ministério. */
    static final String MINISTERIO = "{ministerio}";

    /** Seção padrão ao entrar num ministério pela SideRail ou pelo atalho "Gerenciar". */
    static final String SECAO_INICIAL = "eventos";

    private static final Pattern AREA_DO_MINISTERIO = Pattern.compile("^/ministerios/(\\d+)(?:/([^/]+))?(?:/.*)?$");

    /** Entradas na ordem em que aparecem. O ícone precisa estar em src/main/frontend/icones.json. */
    static final Navegacao PRINCIPAL = new Navegacao(List.of(
            new Entrada(Area.MEMBRO, "Minhas escalas", "Escalas", "calendar-check", "/", Perfil.MEMBRO),
            new Entrada(Area.MEMBRO, "Disponibilidade", "Disponib.", "list-checks", "/disponibilidade", Perfil.MEMBRO),
            new Entrada(
                    Area.MEMBRO,
                    "Gerenciar",
                    "Gerenciar",
                    "layout-dashboard",
                    "/ministerios/" + MINISTERIO + "/" + SECAO_INICIAL,
                    Perfil.MEMBRO),
            new Entrada(Area.MEMBRO, "Ministérios", "Ministérios", "church", "/admin/ministerios", Perfil.ADMIN),
            new Entrada(
                    Area.MINISTERIO,
                    "Eventos",
                    "Eventos",
                    "calendar",
                    "/ministerios/" + MINISTERIO + "/eventos",
                    Perfil.MEMBRO),
            new Entrada(
                    Area.MINISTERIO,
                    "Disponibilidade",
                    "Disponib.",
                    "list-checks",
                    "/ministerios/" + MINISTERIO + "/disponibilidade",
                    Perfil.MEMBRO),
            new Entrada(
                    Area.MINISTERIO,
                    "Membros",
                    "Membros",
                    "users",
                    "/ministerios/" + MINISTERIO + "/membros",
                    Perfil.MEMBRO),
            new Entrada(
                    Area.MINISTERIO,
                    "Funções",
                    "Funções",
                    "layers",
                    "/ministerios/" + MINISTERIO + "/funcoes",
                    Perfil.MEMBRO),
            new Entrada(Area.MINISTERIO, "Ministérios", "Ministérios", "church", "/admin/ministerios", Perfil.ADMIN),
            new Entrada(Area.MINISTERIO, "Minhas escalas", "Minhas", "calendar-check", "/", Perfil.MEMBRO)));

    private final List<Entrada> entradas;

    Navegacao(List<Entrada> entradas) {
        this.entradas = List.copyOf(entradas);
    }

    /**
     * Itens que quem tem essas autoridades vê no caminho atual, com o item da página marcado.
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
        return entradas.stream()
                .filter(entrada -> entrada.area() == local.area())
                .filter(entrada -> nomes.contains(entrada.perfil().authority()))
                .filter(entrada -> !entrada.url().contains(MINISTERIO) || ministerio.isPresent())
                .map(entrada -> item(entrada, ministerio, caminho))
                .toList();
    }

    /**
     * SideRail: os ministérios gerenciados, só dentro da área de um ministério. Cada item leva à mesma seção no outro
     * ministério (de "Membros" da Mídia para "Membros" do Louvor).
     */
    static List<ItemDeNavegacao> ministerios(String caminho, List<MinisterioNaNavegacao> gerenciados) {
        var local = Local.de(caminho);
        if (local.area() != Area.MINISTERIO) {
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
        return new ItemDeNavegacao(entrada.rotulo(), entrada.rotuloCurto(), entrada.icone(), url, estaEm(caminho, url));
    }

    /** "/" só vale para o início; as outras seções valem também para as subpáginas ("/membros/3"). */
    private static boolean estaEm(String caminho, String url) {
        if (url.equals("/")) {
            return caminho.equals("/");
        }
        return caminho.equals(url) || caminho.startsWith(url + "/");
    }

    enum Area {
        MEMBRO,
        MINISTERIO
    }

    record Entrada(Area area, String rotulo, String rotuloCurto, String icone, String url, Perfil perfil) {}

    /** Onde o caminho está: na área do membro ou num ministério, e em qual seção dele. */
    record Local(Area area, Optional<Long> ministerioId, Optional<String> secao) {

        static Local de(String caminho) {
            var area = AREA_DO_MINISTERIO.matcher(caminho);
            if (!area.matches()) {
                return new Local(Area.MEMBRO, Optional.empty(), Optional.empty());
            }
            return new Local(
                    Area.MINISTERIO, Optional.of(Long.valueOf(area.group(1))), Optional.ofNullable(area.group(2)));
        }
    }
}
