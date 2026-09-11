package com.petconnect.api.appointment.application;

import com.petconnect.api.appointment.domain.Appointment;
import com.petconnect.api.appointment.domain.AppointmentStatus;
import com.petconnect.api.appointment.infrastructure.AppointmentRepository;
import com.petconnect.api.appointment.web.AppointmentRequest;
import com.petconnect.api.pet.application.PetService;
import com.petconnect.api.shared.error.ApiException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Casos de uso de consultas. Toda operação valida, via
 * {@link PetService#get(String, String)}, que o {@code petId} pertence ao
 * tutor do token — pet (ou consulta) de outro tutor responde 404.
 */
@Service
public class AppointmentService {

    private final AppointmentRepository appointments;
    private final PetService pets;

    public AppointmentService(AppointmentRepository appointments, PetService pets) {
        this.appointments = appointments;
        this.pets = pets;
    }

    public List<Appointment> list(String tutorId, String petId) {
        pets.get(tutorId, petId);
        return appointments.findByPetIdOrderByScheduledDateAscScheduledTimeAsc(petId);
    }

    public Appointment create(String tutorId, String petId, AppointmentRequest req) {
        pets.get(tutorId, petId);
        Appointment a = new Appointment();
        a.setPetId(petId);
        a.setStatus(AppointmentStatus.CONFIRMED);
        apply(a, req);
        return appointments.save(a);
    }

    public Appointment update(String tutorId, String petId, String appointmentId, AppointmentRequest req) {
        pets.get(tutorId, petId);
        Appointment a = ownedOr404(petId, appointmentId);
        apply(a, req);
        return appointments.save(a);
    }

    private void apply(Appointment a, AppointmentRequest req) {
        a.setScheduledDate(req.scheduledDate());
        a.setScheduledTime(req.scheduledTime());
        a.setVeterinarian(req.veterinarian().trim());
        a.setReason(req.reason().trim());
        if (req.status() != null) {
            a.setStatus(req.status());
        }
    }

    private Appointment ownedOr404(String petId, String appointmentId) {
        Appointment a = appointments.findById(appointmentId)
                .orElseThrow(() -> ApiException.notFound("Consulta não encontrada."));
        if (!petId.equals(a.getPetId())) {
            throw ApiException.notFound("Consulta não encontrada.");
        }
        return a;
    }
}
