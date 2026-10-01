package br.igreja.escala.ministerio.web;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.security.access.prepost.PreAuthorize;

/**
 * Rota que só o gerente do ministério (ou o admin) abre. Vai em todo controller de {@code /ministerios/{ministerioId}},
 * de qualquer módulo; o {@code RotasDoGerenteTest} falha se alguma rota desse caminho ficar sem ela.
 *
 * <p>O método precisa de um parâmetro {@code Long ministerioId} (o {@code @PathVariable}). Sem ele, a expressão recebe
 * nulo e nega o acesso.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@PreAuthorize("@acessoAoMinisterio.podeGerenciar(authentication, #ministerioId)")
public @interface GerenteDoMinisterio {}
