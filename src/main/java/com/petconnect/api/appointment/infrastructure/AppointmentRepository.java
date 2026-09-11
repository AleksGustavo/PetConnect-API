package com.petconnect.api.appointment.infrastructure;

import com.petconnect.api.appointment.domain.Appointment;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface AppointmentRepository extends MongoRepository<Appointment, String> {

    List<Appointment> findByPetIdOrderByScheduledDateAscScheduledTimeAsc(String petId);

    void deleteByPetId(String petId);
}
