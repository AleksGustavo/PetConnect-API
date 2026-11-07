package com.petconnect.api.notification.infrastructure;

import com.petconnect.api.notification.domain.TutorNotification;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface TutorNotificationRepository extends MongoRepository<TutorNotification, String> {

    List<TutorNotification> findByOwnerUid(String ownerUid);

    long deleteByPetKey(String petKey);

    long deleteByOwnerUid(String ownerUid);
}
