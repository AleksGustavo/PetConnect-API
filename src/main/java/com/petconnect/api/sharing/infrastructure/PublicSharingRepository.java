package com.petconnect.api.sharing.infrastructure;

import com.petconnect.api.sharing.domain.PublicSharing;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface PublicSharingRepository extends MongoRepository<PublicSharing, String> {

    Optional<PublicSharing> findByPetKey(String petKey);

    Optional<PublicSharing> findByPublicId(String publicId);

    List<PublicSharing> findByOwnerUid(String ownerUid);

    long deleteByPetKey(String petKey);

    long deleteByOwnerUid(String ownerUid);
}
