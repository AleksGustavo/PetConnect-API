package com.petconnect.api.location.infrastructure;

import com.petconnect.api.location.domain.Location;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Collection;

public interface LocationRepository extends MongoRepository<Location, String> {

    boolean existsByLegacyPetId(String legacyPetId);

    void deleteByPetIdIn(Collection<String> petIds);
}
