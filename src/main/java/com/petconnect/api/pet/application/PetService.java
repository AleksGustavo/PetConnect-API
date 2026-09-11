package com.petconnect.api.pet.application;

import com.petconnect.api.appointment.infrastructure.AppointmentRepository;
import com.petconnect.api.location.infrastructure.LocationRepository;
import com.petconnect.api.pet.domain.Pet;
import com.petconnect.api.pet.infrastructure.PetRepository;
import com.petconnect.api.pet.web.CreatePetRequest;
import com.petconnect.api.pet.web.UpdatePetRequest;
import com.petconnect.api.shared.error.ApiException;
import com.petconnect.api.vaccine.infrastructure.VaccineRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Casos de uso de pets. Toda operação por id verifica a posse
 * ({@code tutorId == usuário logado}); pet de outro tutor responde 404
 * (não vaza existência).
 */
@Service
public class PetService {

    private final PetRepository pets;
    private final LocationRepository locations;
    private final VaccineRepository vaccines;
    private final AppointmentRepository appointments;

    public PetService(PetRepository pets, LocationRepository locations, VaccineRepository vaccines,
                      AppointmentRepository appointments) {
        this.pets = pets;
        this.locations = locations;
        this.vaccines = vaccines;
        this.appointments = appointments;
    }

    public List<Pet> list(String tutorId) {
        return pets.findByTutorId(tutorId).stream()
                .sorted((a, b) -> nullSafe(a.getName()).compareToIgnoreCase(nullSafe(b.getName())))
                .toList();
    }

    public Pet get(String tutorId, String petId) {
        return ownedOr404(tutorId, petId);
    }

    public Pet create(String tutorId, CreatePetRequest req) {
        Pet p = new Pet();
        p.setTutorId(tutorId);
        p.setName(req.name().trim());
        if (req.species() != null) p.setSpecies(req.species());
        if (req.gender() != null) p.setGender(req.gender());
        if (req.status() != null) p.setStatus(req.status());
        p.setBreed(trimToNull(req.breed()));
        p.setColor(trimToNull(req.color()));
        p.setSize(req.size());
        p.setWeightKg(req.weightKg());
        p.setBirthDate(req.birthDate());
        p.setVaccinatedFlag(req.vaccinatedFlag());
        p.setPublicContactPhone(trimToNull(req.publicContactPhone()));
        p.setPhotoUrl(trimToNull(req.photoUrl()));
        return pets.save(p);
    }

    public Pet update(String tutorId, String petId, UpdatePetRequest req) {
        Pet p = ownedOr404(tutorId, petId);
        if (req.name() != null && !req.name().isBlank()) p.setName(req.name().trim());
        if (req.species() != null) p.setSpecies(req.species());
        if (req.gender() != null) p.setGender(req.gender());
        if (req.size() != null) p.setSize(req.size());
        if (req.status() != null) p.setStatus(req.status());
        if (req.breed() != null) p.setBreed(trimToNull(req.breed()));
        if (req.color() != null) p.setColor(trimToNull(req.color()));
        if (req.weightKg() != null) p.setWeightKg(req.weightKg());
        if (req.birthDate() != null) p.setBirthDate(req.birthDate());
        if (req.vaccinatedFlag() != null) p.setVaccinatedFlag(req.vaccinatedFlag());
        if (req.publicContactPhone() != null) p.setPublicContactPhone(trimToNull(req.publicContactPhone()));
        if (req.photoUrl() != null) p.setPhotoUrl(trimToNull(req.photoUrl()));
        return pets.save(p);
    }

    public void delete(String tutorId, String petId) {
        Pet p = ownedOr404(tutorId, petId);
        locations.deleteByPetIdIn(List.of(p.getId()));
        vaccines.deleteByPetId(p.getId());
        appointments.deleteByPetId(p.getId());
        pets.delete(p);
    }

    private Pet ownedOr404(String tutorId, String petId) {
        Pet p = pets.findById(petId)
                .orElseThrow(() -> ApiException.notFound("Pet não encontrado."));
        if (!tutorId.equals(p.getTutorId())) {
            throw ApiException.notFound("Pet não encontrado.");
        }
        return p;
    }

    private static String trimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static String nullSafe(String s) {
        return s == null ? "" : s;
    }
}
