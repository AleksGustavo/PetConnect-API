package com.petconnect.api.shared.web;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Limitador simples por janela deslizante, em memória. Suficiente para uma
 * única instância (não distribuído — se algum dia rodar mais de um pod da
 * API, isto precisa virar um contador compartilhado, ex. Redis).
 */
@Component
public class RateLimiter {

    private final Map<String, ConcurrentLinkedDeque<Long>> hits = new ConcurrentHashMap<>();

    /** @return {@code true} se a requisição pode prosseguir; {@code false} se estourou o limite. */
    public boolean tryConsume(String key, int maxRequests, Duration window) {
        long now = System.currentTimeMillis();
        long windowStart = now - window.toMillis();
        ConcurrentLinkedDeque<Long> deque = hits.computeIfAbsent(key, k -> new ConcurrentLinkedDeque<>());

        synchronized (deque) {
            while (!deque.isEmpty() && deque.peekFirst() < windowStart) {
                deque.pollFirst();
            }
            if (deque.size() >= maxRequests) {
                return false;
            }
            deque.addLast(now);
            return true;
        }
    }

    /** Usado só em teste, para isolar execuções entre casos. */
    public void clear() {
        hits.clear();
    }
}
