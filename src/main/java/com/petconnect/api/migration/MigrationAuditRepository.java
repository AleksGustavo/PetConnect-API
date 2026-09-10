package com.petconnect.api.migration;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface MigrationAuditRepository extends MongoRepository<MigrationAudit, String> {

    long countByOutcome(MigrationAudit.Outcome outcome);
}
