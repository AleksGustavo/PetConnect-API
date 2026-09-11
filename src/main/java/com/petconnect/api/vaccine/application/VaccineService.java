package com.petconnect.api.vaccine.application;

import com.petconnect.api.pet.application.PetService;
import com.petconnect.api.shared.error.ApiException;
import com.petconnect.api.vaccine.domain.Vaccine;
import com.petconnect.api.vaccine.infrastructure.VaccineRepository;
import com.petconnect.api.vaccine.web.VaccineRequest;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Casos de uso da carteira de vacina. Toda operação valida, via
 * {@link PetService#get(String, String)}, que o {@code petId} pertence ao
 * tutor do token — pet (ou vacina) de outro tutor responde 404.
 */
@Service
public class VaccineService {

    private final VaccineRepository vaccines;
    private final PetService pets;

    public VaccineService(VaccineRepository vaccines, PetService pets) {
        this.vaccines = vaccines;
        this.pets = pets;
    }

    public List<Vaccine> list(String tutorId, String petId) {
        pets.get(tutorId, petId); // 404 se o pet não é do tutor
        return vaccines.findByPetIdOrderByAppliedAtAsc(petId);
    }

    public Vaccine create(String tutorId, String petId, VaccineRequest req) {
        pets.get(tutorId, petId);
        Vaccine v = new Vaccine();
        v.setPetId(petId);
        apply(v, req);
        return vaccines.save(v);
    }

    public Vaccine update(String tutorId, String petId, String vaccineId, VaccineRequest req) {
        pets.get(tutorId, petId);
        Vaccine v = ownedOr404(petId, vaccineId);
        apply(v, req);
        return vaccines.save(v);
    }

    public void delete(String tutorId, String petId, String vaccineId) {
        pets.get(tutorId, petId);
        vaccines.delete(ownedOr404(petId, vaccineId));
    }

    private void apply(Vaccine v, VaccineRequest req) {
        v.setName(req.name().trim());
        v.setAppliedAt(req.appliedAt());
        v.setNextDoseAt(req.nextDoseAt());
        v.setVeterinarian(trimToNull(req.veterinarian()));
        v.setNotes(trimToNull(req.notes()));
    }

    private Vaccine ownedOr404(String petId, String vaccineId) {
        Vaccine v = vaccines.findById(vaccineId)
                .orElseThrow(() -> ApiException.notFound("Vacina não encontrada."));
        if (!petId.equals(v.getPetId())) {
            throw ApiException.notFound("Vacina não encontrada.");
        }
        return v;
    }

    private static String trimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
