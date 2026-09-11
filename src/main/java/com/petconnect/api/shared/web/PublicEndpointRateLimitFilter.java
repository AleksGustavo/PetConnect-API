package com.petconnect.api.shared.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.petconnect.api.shared.config.RateLimitProperties;
import com.petconnect.api.shared.error.ApiError;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;

/**
 * Limita requisições por IP nos endpoints públicos (FASE 9) — os únicos sem
 * autenticação, logo os únicos onde um IP não tem nenhum outro freio. Leitura
 * (scan do QR) e escrita (relato de avistamento) têm limites separados; a
 * escrita é mais restrita por ser o vetor de spam.
 */
public class PublicEndpointRateLimitFilter extends OncePerRequestFilter {

    private static final Duration WINDOW = Duration.ofMinutes(1);
    private static final String PUBLIC_PREFIX = "/api/v1/public/";

    private final RateLimiter limiter;
    private final RateLimitProperties props;
    private final ObjectMapper objectMapper;

    public PublicEndpointRateLimitFilter(RateLimiter limiter, RateLimitProperties props, ObjectMapper objectMapper) {
        this.limiter = limiter;
        this.props = props;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        boolean isWrite = HttpMethod.POST.matches(request.getMethod());
        int limit = isWrite ? props.publicWritePerMinute() : props.publicReadPerMinute();
        String key = clientIp(request) + (isWrite ? ":write" : ":read");

        if (!limiter.tryConsume(key, limit, WINDOW)) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            objectMapper.writeValue(response.getWriter(), ApiError.of(
                    HttpStatus.TOO_MANY_REQUESTS.value(), "RATE_LIMITED",
                    "Muitas requisições. Tente novamente em instantes."));
            return;
        }

        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(PUBLIC_PREFIX);
    }

    /**
     * {@code X-Forwarded-For} quando atrás de um proxy/load balancer (o primeiro
     * IP da lista — pode ser forjado pelo cliente se o proxy não sobrescrever o
     * header; aceitável para este limite de "melhor esforço", não é controle de
     * acesso). Sem proxy, usa o IP da conexão direta.
     */
    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
