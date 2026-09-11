package com.petconnect.api.medicalrecord.infrastructure;

import com.petconnect.api.medicalrecord.domain.MedicalRecord;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface MedicalRecordRepository extends MongoRepository<MedicalRecord, String> {

    List<MedicalRecord> findByPetIdOrderByRecordedAtAsc(String petId);

    void deleteByPetId(String petId);
}
