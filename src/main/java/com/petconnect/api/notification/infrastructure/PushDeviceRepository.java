package com.petconnect.api.notification.infrastructure;

import com.petconnect.api.notification.domain.PushDevice;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface PushDeviceRepository extends MongoRepository<PushDevice, String> {

    Optional<PushDevice> findByToken(String token);

    List<PushDevice> findByOwnerUid(String ownerUid);

    long deleteByToken(String token);

    long deleteByTokenAndOwnerUid(String token, String ownerUid);

    long deleteByOwnerUid(String ownerUid);
}
