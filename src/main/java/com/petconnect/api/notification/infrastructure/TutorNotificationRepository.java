package com.petconnect.api.notification.infrastructure;

import com.petconnect.api.notification.domain.TutorNotification;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface TutorNotificationRepository extends MongoRepository<TutorNotification, String> {

    long deleteByOwnerUid(String ownerUid);
}
