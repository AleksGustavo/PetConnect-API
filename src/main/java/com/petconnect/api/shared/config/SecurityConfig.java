package com.petconnect.api.shared.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.petconnect.api.shared.error.ApiError;
import com.petconnect.api.shared.security.AuthenticatedUserResolver;
import com.petconnect.api.shared.security.FirebaseTokenAuthenticationFilter;
import com.petconnect.api.shared.security.FirebaseTokenVerifier;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Segurança da API. Stateless — sem sessão, sem CSRF.
 *
 * <p>Rotas públicas (health, docs, ping) liberadas; o resto exige autenticação.
 * Quando há credencial do Firebase configurada, o
 * {@link FirebaseTokenAuthenticationFilter} valida o ID Token e popula o
 * {@code SecurityContext}. Sem credencial, o filtro não é registrado e as
 * rotas protegidas simplesmente respondem 401.
 */
@Configuration
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    /** Caminhos abertos sem autenticação. */
    static final String[] PUBLIC_PATHS = {
            "/actuator/health",
            "/actuator/health/**",
            "/actuator/info",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/api/v1/ping"
    };

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http,
                                    @Qualifier("corsConfigurationSource") CorsConfigurationSource corsConfigurationSource,
                                    ObjectMapper mapper,
                                    org.springframework.beans.factory.ObjectProvider<FirebaseTokenVerifier> verifier,
                                    org.springframework.beans.factory.ObjectProvider<AuthenticatedUserResolver> userResolver) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(c -> c.configurationSource(corsConfigurationSource))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((req, res, e) ->
                                writeError(res, mapper, HttpStatus.UNAUTHORIZED,
                                        "UNAUTHENTICATED", "Credenciais ausentes ou inválidas."))
                        .accessDeniedHandler((req, res, e) ->
                                writeError(res, mapper, HttpStatus.FORBIDDEN,
                                        "FORBIDDEN", "Acesso negado.")));

        FirebaseTokenVerifier v = verifier.getIfAvailable();
        AuthenticatedUserResolver r = userResolver.getIfAvailable();
        if (v != null && r != null) {
            http.addFilterBefore(new FirebaseTokenAuthenticationFilter(v, r, mapper),
                    UsernamePasswordAuthenticationFilter.class);
        } else {
            log.warn("Filtro de autenticação Firebase NÃO registrado (verifier/resolver ausente). "
                    + "Rotas protegidas responderão 401.");
        }

        return http.build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(CorsProperties props) {
        CorsConfiguration cfg = new CorsConfiguration();
        cfg.setAllowedOrigins(props.allowedOrigins());
        cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cfg.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        cfg.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cfg);
        return source;
    }

    private static void writeError(HttpServletResponse res, ObjectMapper mapper,
                                   HttpStatus status, String code, String message) throws java.io.IOException {
        res.setStatus(status.value());
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        res.setCharacterEncoding("UTF-8");
        mapper.writeValue(res.getWriter(), ApiError.of(status.value(), code, message));
    }
}
