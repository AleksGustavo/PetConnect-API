package com.petconnect.api.notification.infrastructure;

import com.petconnect.api.notification.domain.PetNotificationPreferences;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface PetNotificationPreferencesRepository extends MongoRepository<PetNotificationPreferences, String> {

    Optional<PetNotificationPreferences> findByPetKey(String petKey);

    long deleteByPetKey(String petKey);

    long deleteByOwnerUid(String ownerUid);
}
