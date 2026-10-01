package br.igreja.escala.identidade.config;

import br.igreja.escala.identidade.service.UsuarioDetailsService;
import br.igreja.escala.identidade.web.FalhaDeLoginHandler;
import br.igreja.escala.identidade.web.SessaoEncerradaHandler;
import br.igreja.escala.identidade.web.SessoesAbertas;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.session.HttpSessionEventPublisher;

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
            SessionRegistry sessoes,
            @Value("${escala.seguranca.chave-lembrar-me}") String chaveLembrarMe) {
        http.authorizeHttpRequests(rotas -> rotas.requestMatchers(
                                "/login", "/css/**", "/js/**", "/fontes/**", "/icones/**", "/favicon.ico", "/error")
                        .permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**")
                        .permitAll()
                        // Os controllers do admin também exigem o perfil; aqui é a segunda trava.
                        .requestMatchers("/admin/**")
                        .hasRole("ADMIN")
                        .anyRequest()
                        .authenticated())
                .formLogin(login -> login.loginPage("/login")
                        .usernameParameter("email")
                        .passwordParameter("senha")
                        .failureHandler(new FalhaDeLoginHandler())
                        .permitAll())
                .rememberMe(lembrar -> lembrar.key(chaveLembrarMe)
                        .rememberMeParameter("lembrar")
                        .tokenValiditySeconds((int) VALIDADE_LEMBRAR_ME.toSeconds())
                        .userDetailsService(usuarios))
                // Sem limite de sessões: o registro serve para encerrar as de alguém (SessoesAbertas).
                .sessionManagement(sessao -> sessao.sessionConcurrency(concorrencia -> concorrencia
                        .maximumSessions(-1)
                        .sessionRegistry(sessoes)
                        .expiredSessionStrategy(new SessaoEncerradaHandler())))
                .logout(logout -> logout.logoutSuccessUrl("/login?saiu"));
        return http.build();
    }

    /** Sessões abertas por usuário, inclusive as do "continuar conectado". */
    @Bean
    SessionRegistry sessionRegistry() {
        return new SessionRegistryImpl();
    }

    /** Avisa o registro quando uma sessão acaba ou troca de id no servidor. */
    @Bean
    HttpSessionEventPublisher httpSessionEventPublisher() {
        return new HttpSessionEventPublisher();
    }

    @Bean
    SessoesAbertas sessoesAbertas(SessionRegistry sessoes) {
        return new SessoesAbertas(sessoes);
    }
}
