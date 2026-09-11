package com.petconnect.api.vaccine.infrastructure;

import com.petconnect.api.vaccine.domain.Vaccine;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Collection;
import java.util.List;

public interface VaccineRepository extends MongoRepository<Vaccine, String> {

    List<Vaccine> findByPetIdOrderByAppliedAtAsc(String petId);

    void deleteByPetId(String petId);

    void deleteByPetIdIn(Collection<String> petIds);
}
