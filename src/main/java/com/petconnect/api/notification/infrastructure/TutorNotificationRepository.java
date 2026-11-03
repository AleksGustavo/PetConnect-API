package com.petconnect.api.notification.infrastructure;

import com.petconnect.api.notification.domain.TutorNotification;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.Instant;

public interface TutorNotificationRepository extends MongoRepository<TutorNotification, String> {

    boolean existsByPetKeyAndTypeAndCreatedAtAfter(String petKey, TutorNotification.Type type, Instant after);

    long deleteByOwnerUid(String ownerUid);
}
