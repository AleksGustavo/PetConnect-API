package com.petconnect.api.qractivity.infrastructure;

import com.petconnect.api.qractivity.domain.QrAccessEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.Instant;
import java.util.List;

public interface QrAccessEventRepository extends MongoRepository<QrAccessEvent, String> {

    List<QrAccessEvent> findByPetKeyOrderByOccurredAtDesc(String petKey, Pageable page);

    long countByPetKeyAndOccurredAtAfter(String petKey, Instant after);

    long deleteByPetKey(String petKey);
}
