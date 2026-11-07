package com.petconnect.api.notification.infrastructure;

import com.petconnect.api.notification.domain.PetNotificationPreferences;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface PetNotificationPreferencesRepository extends MongoRepository<PetNotificationPreferences, String> {

    Optional<PetNotificationPreferences> findByPetKey(String petKey);

    List<PetNotificationPreferences> findByOwnerUid(String ownerUid);

    long deleteByPetKey(String petKey);

    long deleteByOwnerUid(String ownerUid);
}
