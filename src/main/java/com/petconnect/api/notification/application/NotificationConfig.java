package com.petconnect.api.notification.application;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Onde os avisos ao tutor rodam. Em produção, uma thread virtual por aviso,
 * fora da requisição da página pública. Nos testes
 * ({@code petconnect.notifications.async: false}), na própria thread, para
 * dar para conferir o resultado logo depois da chamada.
 */
@Configuration
public class NotificationConfig {

    /**
     * Tipo próprio, e não {@code Executor}: um bean {@code Executor} faria o
     * Spring Boot deixar de criar o executor padrão da aplicação.
     */
    public static final class NotificationExecutor implements AutoCloseable {

        private final ExecutorService pool;

        NotificationExecutor(ExecutorService pool) {
            this.pool = pool;
        }

        void execute(Runnable task) {
            if (pool == null) {
                task.run();
            } else {
                pool.execute(task);
            }
        }

        @Override
        public void close() {
            if (pool != null) {
                pool.close();
            }
        }
    }

    @Bean
    NotificationExecutor notificationExecutor(@Value("${petconnect.notifications.async:true}") boolean async) {
        return new NotificationExecutor(async ? Executors.newVirtualThreadPerTaskExecutor() : null);
    }
}
