package com.petconnect.api.sharing.infrastructure;

import com.petconnect.api.sharing.domain.PublicSharing;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface PublicSharingRepository extends MongoRepository<PublicSharing, String> {

    Optional<PublicSharing> findByPetKey(String petKey);

    Optional<PublicSharing> findByPublicId(String publicId);

    void deleteByPetKey(String petKey);

    long deleteByOwnerUid(String ownerUid);
}
