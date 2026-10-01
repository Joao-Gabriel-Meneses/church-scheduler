package br.igreja.escala.compartilhado.web;

import java.util.List;
import org.springframework.security.core.Authentication;

/**
 * Ministérios que o usuário logado gerencia (o admin, todos), em ordem de nome. Implementado pelo módulo ministerio;
 * a interface fica aqui para a navegação não depender dele.
 */
public interface MinisteriosGerenciados {

    List<MinisterioNaNavegacao> de(Authentication autenticacao);
}
