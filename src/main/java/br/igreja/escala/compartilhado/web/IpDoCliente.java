package br.igreja.escala.compartilhado.web;

import jakarta.servlet.http.HttpServletRequest;
import java.util.regex.Pattern;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * IP de quem fez a requisição. Em produção o app só é alcançável pelo Cloudflare Tunnel, e o Cloudflare preenche o
 * cabeçalho {@code CF-Connecting-IP} com o IP do usuário (sobrescrevendo o que vier do cliente). Sem o cabeçalho
 * (desenvolvimento, testes), vale o IP da requisição.
 */
public final class IpDoCliente {

    public static final String CABECALHO = "CF-Connecting-IP";

    /** IPv6 com zona e IPv4 mapeado cabem em 45 caracteres. */
    public static final int TAMANHO_MAXIMO = 45;

    private static final Pattern IP = Pattern.compile("[0-9a-fA-F:.]+");

    private IpDoCliente() {}

    public static String de(HttpServletRequest requisicao) {
        var cabecalho = requisicao.getHeader(CABECALHO);
        if (cabecalho != null) {
            var ip = cabecalho.strip();
            if (ip.length() <= TAMANHO_MAXIMO && IP.matcher(ip).matches()) {
                return ip;
            }
        }
        return requisicao.getRemoteAddr();
    }

    /** O IP da requisição em andamento nesta thread, ou {@code null} fora de uma requisição. */
    public static String atual() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes atributos) {
            return de(atributos.getRequest());
        }
        return null;
    }
}
