package br.igreja.escala.identidade.config;

import br.igreja.escala.identidade.service.UsuarioDetailsService;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/** Só em aplicação web: sem servidor (ex.: validação de migrações) não há rotas para proteger. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication
@EnableMethodSecurity
public class SecurityConfig {

    /** No celular o membro não deve precisar digitar a senha a cada acesso. */
    private static final Duration VALIDADE_LEMBRAR_ME = Duration.ofDays(30);

    @Bean
    SecurityFilterChain filtros(
            HttpSecurity http,
            UsuarioDetailsService usuarios,
            @Value("${escala.seguranca.chave-lembrar-me}") String chaveLembrarMe) {
        http.authorizeHttpRequests(
                        rotas -> rotas.requestMatchers("/login", "/css/**", "/js/**", "/favicon.ico", "/error")
                                .permitAll()
                                .requestMatchers("/actuator/health", "/actuator/health/**")
                                .permitAll()
                                .anyRequest()
                                .authenticated())
                .formLogin(login -> login.loginPage("/login")
                        .usernameParameter("email")
                        .passwordParameter("senha")
                        .failureUrl("/login?erro")
                        .permitAll())
                .rememberMe(lembrar -> lembrar.key(chaveLembrarMe)
                        .rememberMeParameter("lembrar")
                        .tokenValiditySeconds((int) VALIDADE_LEMBRAR_ME.toSeconds())
                        .userDetailsService(usuarios))
                .logout(logout -> logout.logoutSuccessUrl("/login?saiu"));
        return http.build();
    }
}
