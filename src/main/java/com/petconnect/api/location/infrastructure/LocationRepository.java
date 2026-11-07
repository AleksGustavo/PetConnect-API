package com.petconnect.api.location.infrastructure;

import com.petconnect.api.location.domain.Location;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Collection;
import java.util.List;

public interface LocationRepository extends MongoRepository<Location, String> {

    boolean existsByLegacyPetId(String legacyPetId);

    boolean existsByClientRequestId(String clientRequestId);

    void deleteByPetIdIn(Collection<String> petIds);

    long deleteByPetId(String petId);

    long deleteByLegacyPetId(String legacyPetId);

    List<Location> findByPetIdOrderByReportedAtDesc(String petId);

    List<Location> findByLegacyPetIdOrderByReportedAtDesc(String legacyPetId, Pageable page);

    List<Location> findByPetIdOrderByReportedAtDesc(String petId, Pageable page);
}
