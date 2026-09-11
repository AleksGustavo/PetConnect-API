package com.petconnect.api.pet.infrastructure;

import com.petconnect.api.pet.domain.Pet;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface PetRepository extends MongoRepository<Pet, String> {

    Optional<Pet> findByLegacyFirestoreId(String legacyFirestoreId);

    Optional<Pet> findByPublicId(String publicId);

    List<Pet> findByTutorId(String tutorId);
}
