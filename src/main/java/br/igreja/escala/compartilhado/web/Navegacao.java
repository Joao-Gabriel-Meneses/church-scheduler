package br.igreja.escala.compartilhado.web;

import br.igreja.escala.identidade.domain.Perfil;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.core.GrantedAuthority;

/**
 * Navegação principal: NavPills no desktop e barra inferior no celular (templates/componentes/navegacao.html). Cada
 * página entra aqui quando existir; o gerente, que é por ministério, entra com as membresias na Fase 1.
 */
public final class Navegacao {

    /** Entradas na ordem em que aparecem. O ícone precisa estar em src/main/frontend/icones.json. */
    static final Navegacao PRINCIPAL =
            new Navegacao(List.of(new Entrada("Minhas escalas", "Escalas", "calendar-check", "/", Perfil.MEMBRO)));

    private final List<Entrada> entradas;

    Navegacao(List<Entrada> entradas) {
        this.entradas = List.copyOf(entradas);
    }

    /**
     * Itens que quem tem essas autoridades vê, com o item do caminho atual marcado.
     *
     * @param caminho caminho da requisição sem o contexto ("/membros/3")
     */
    List<ItemDeNavegacao> itens(Collection<? extends GrantedAuthority> autoridades, String caminho) {
        Set<String> nomes =
                autoridades.stream().map(GrantedAuthority::getAuthority).collect(Collectors.toSet());
        return entradas.stream()
                .filter(entrada -> nomes.contains(entrada.perfil().authority()))
                .map(entrada -> new ItemDeNavegacao(
                        entrada.rotulo(),
                        entrada.rotuloCurto(),
                        entrada.icone(),
                        entrada.url(),
                        estaEm(caminho, entrada.url())))
                .toList();
    }

    /** "/" só vale para o início; as outras seções valem também para as subpáginas ("/membros/3"). */
    private static boolean estaEm(String caminho, String url) {
        if (url.equals("/")) {
            return caminho.equals("/");
        }
        return caminho.equals(url) || caminho.startsWith(url + "/");
    }

    record Entrada(String rotulo, String rotuloCurto, String icone, String url, Perfil perfil) {}
}
