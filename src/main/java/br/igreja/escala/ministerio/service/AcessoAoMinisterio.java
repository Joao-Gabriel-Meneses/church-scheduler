package br.igreja.escala.ministerio.service;

import br.igreja.escala.identidade.domain.Perfil;
import br.igreja.escala.identidade.domain.UsuarioAutenticado;
import br.igreja.escala.ministerio.repository.MembresiaRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Quem gerencia cada ministério: o admin, todos; o gerente, só aqueles em que a membresia dele é de gerente. Serviço
 * público, usado pelas rotas do gerente de todos os módulos (ver {@code GerenteDoMinisterio}).
 */
@Service(AcessoAoMinisterio.NOME)
public class AcessoAoMinisterio {

    /** Nome do bean, citado na expressão do {@code @PreAuthorize}. */
    public static final String NOME = "acessoAoMinisterio";

    private final MembresiaRepository membresias;

    AcessoAoMinisterio(MembresiaRepository membresias) {
        this.membresias = membresias;
    }

    /**
     * @param ministerioId vem do caminho da rota; nulo nega (a rota esqueceu o parâmetro)
     */
    @Transactional(readOnly = true)
    public boolean podeGerenciar(Authentication autenticacao, Long ministerioId) {
        if (ministerioId == null
                || autenticacao == null
                || !(autenticacao.getPrincipal() instanceof UsuarioAutenticado usuario)) {
            return false;
        }
        return ehAdmin(autenticacao)
                || membresias.existsByUsuarioIdAndMinisterioIdAndGerenteTrue(usuario.getId(), ministerioId);
    }

    static boolean ehAdmin(Authentication autenticacao) {
        return autenticacao.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(Perfil.ADMIN.authority()::equals);
    }
}
