package br.igreja.escala.ministerio.service;

import br.igreja.escala.compartilhado.web.MinisterioNaNavegacao;
import br.igreja.escala.compartilhado.web.MinisteriosGerenciados;
import br.igreja.escala.identidade.domain.UsuarioAutenticado;
import br.igreja.escala.ministerio.domain.Ministerio;
import br.igreja.escala.ministerio.repository.MembresiaRepository;
import br.igreja.escala.ministerio.repository.MinisterioRepository;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Ministérios da SideRail e do atalho "Gerenciar": os mesmos que o {@link AcessoAoMinisterio} deixa abrir. */
@Service
class MinisteriosGerenciadosService implements MinisteriosGerenciados {

    private final MinisterioRepository ministerios;
    private final MembresiaRepository membresias;

    MinisteriosGerenciadosService(MinisterioRepository ministerios, MembresiaRepository membresias) {
        this.ministerios = ministerios;
        this.membresias = membresias;
    }

    @Override
    @Transactional(readOnly = true)
    public List<MinisterioNaNavegacao> de(Authentication autenticacao) {
        if (autenticacao == null || !(autenticacao.getPrincipal() instanceof UsuarioAutenticado usuario)) {
            return List.of();
        }
        var gerenciados = AcessoAoMinisterio.ehAdmin(autenticacao)
                ? ministerios.findAllByOrderByNomeAsc()
                : membresias.ministeriosGerenciadosPor(usuario.getId());
        return gerenciados.stream()
                .map(MinisteriosGerenciadosService::naNavegacao)
                .toList();
    }

    private static MinisterioNaNavegacao naNavegacao(Ministerio ministerio) {
        return new MinisterioNaNavegacao(
                ministerio.getId(), ministerio.getNome(), ministerio.getIcone().lucide());
    }
}
