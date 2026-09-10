package com.petconnect.api.migration;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Trilha de auditoria: 1 documento por registro do Firestore processado
 * (migrado, ignorado ou com falha). Guarda o snapshot cru para permitir
 * reprocessar/rollback. Coleção {@code migration_audit}.
 */
@Document(collection = "migration_audit")
public class MigrationAudit {

    public enum Outcome { MIGRATED, SKIPPED, FAILED }

    @Id
    private String id;

    private String sourceCollection;
    private String sourceId;
    private String targetCollection;
    private String targetId;
    private Outcome outcome;
    private String reason;
    private List<String> warnings;
    private Map<String, Object> raw;
    private Instant processedAt = Instant.now();

    public static MigrationAudit migrated(String sourceCollection, String sourceId, String targetCollection,
                                          String targetId, List<String> warnings, Map<String, Object> raw) {
        MigrationAudit a = new MigrationAudit();
        a.sourceCollection = sourceCollection;
        a.sourceId = sourceId;
        a.targetCollection = targetCollection;
        a.targetId = targetId;
        a.outcome = Outcome.MIGRATED;
        a.warnings = warnings;
        a.raw = raw;
        return a;
    }

    public static MigrationAudit skipped(String sourceCollection, String sourceId, String reason,
                                         Map<String, Object> raw) {
        MigrationAudit a = new MigrationAudit();
        a.sourceCollection = sourceCollection;
        a.sourceId = sourceId;
        a.outcome = Outcome.SKIPPED;
        a.reason = reason;
        a.raw = raw;
        return a;
    }

    public static MigrationAudit failed(String sourceCollection, String sourceId, String reason,
                                        Map<String, Object> raw) {
        MigrationAudit a = new MigrationAudit();
        a.sourceCollection = sourceCollection;
        a.sourceId = sourceId;
        a.outcome = Outcome.FAILED;
        a.reason = reason;
        a.raw = raw;
        return a;
    }

    public String getId() {
        return id;
    }

    public String getSourceCollection() {
        return sourceCollection;
    }

    public String getSourceId() {
        return sourceId;
    }

    public String getTargetCollection() {
        return targetCollection;
    }

    public String getTargetId() {
        return targetId;
    }

    public Outcome getOutcome() {
        return outcome;
    }

    public String getReason() {
        return reason;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public Map<String, Object> getRaw() {
        return raw;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }
}
