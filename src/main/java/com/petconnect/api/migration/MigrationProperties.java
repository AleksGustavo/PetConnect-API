package com.petconnect.api.migration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param source       caminho do {@code firestore_backup.json} (fora do repo)
 * @param exitWhenDone encerra a aplicação após migrar (padrão: {@code true})
 */
@ConfigurationProperties(prefix = "petconnect.migration")
public record MigrationProperties(String source, Boolean exitWhenDone) {

    public boolean shouldExit() {
        return exitWhenDone == null || exitWhenDone;
    }
}
