package com.petconnect.api.migration;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Resultado consolidado de uma execução da migração. */
public record MigrationReport(
        Instant startedAt,
        Instant finishedAt,
        String source,
        Section users,
        Section pets,
        Section locations,
        List<String> excludedUserIds,
        List<String> excludedPetIds,
        long excludedLocations,
        Map<String, Long> warningCounts
) {

    /** Contagens de uma coleção. */
    public record Section(long total, long migrated, long skipped, long failed, long withWarnings) {
    }
}
