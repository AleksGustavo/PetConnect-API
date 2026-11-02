package com.petconnect.api.sharing.infrastructure;

import com.petconnect.api.sharing.domain.ConsentEvent;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface ConsentEventRepository extends MongoRepository<ConsentEvent, String> {

    List<ConsentEvent> findByPetKeyOrderByAtAsc(String petKey);
}
