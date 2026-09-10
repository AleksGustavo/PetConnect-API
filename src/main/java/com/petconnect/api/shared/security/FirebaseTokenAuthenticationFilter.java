package com.petconnect.api.shared.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.petconnect.api.shared.error.ApiError;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Lê {@code Authorization: Bearer <Firebase ID Token>}, verifica o token e
 * popula o {@link SecurityContextHolder}. Sem header, segue a cadeia sem
 * autenticar (a autorização decide o 401). Token presente e inválido → 401
 * imediato no formato {@link ApiError}.
 */
public class FirebaseTokenAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(FirebaseTokenAuthenticationFilter.class);
    private static final String BEARER_PREFIX = "Bearer ";

    private final FirebaseTokenVerifier verifier;
    private final AuthenticatedUserResolver userResolver;
    private final ObjectMapper objectMapper;

    public FirebaseTokenAuthenticationFilter(FirebaseTokenVerifier verifier,
                                             AuthenticatedUserResolver userResolver,
                                             ObjectMapper objectMapper) {
        this.verifier = verifier;
        this.userResolver = userResolver;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        String idToken = header.substring(BEARER_PREFIX.length()).trim();
        try {
            VerifiedToken verified = verifier.verify(idToken);
            AuthenticatedUser principal = userResolver.resolve(verified);

            List<SimpleGrantedAuthority> authorities = principal.roles().stream()
                    .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                    .toList();

            var authentication = new UsernamePasswordAuthenticationToken(principal, null, authorities);
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);

            filterChain.doFilter(request, response);
        } catch (TokenVerificationException e) {
            SecurityContextHolder.clearContext();
            log.debug("Falha ao verificar Firebase ID Token: {}", e.getMessage());
            writeUnauthorized(response);
        }
    }

    private void writeUnauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(),
                ApiError.of(HttpStatus.UNAUTHORIZED.value(), "UNAUTHENTICATED",
                        "Token ausente, inválido ou expirado."));
    }

    /** Nunca filtrar as rotas públicas. */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return List.of(
                "/actuator/health",
                "/actuator/info",
                "/v3/api-docs",
                "/swagger-ui",
                "/api/v1/ping"
        ).stream().anyMatch(path::startsWith);
    }
}
