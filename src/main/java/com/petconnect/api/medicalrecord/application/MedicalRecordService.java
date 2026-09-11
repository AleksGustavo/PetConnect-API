package com.petconnect.api.medicalrecord.application;

import com.petconnect.api.medicalrecord.domain.MedicalOrigin;
import com.petconnect.api.medicalrecord.domain.MedicalRecord;
import com.petconnect.api.medicalrecord.infrastructure.MedicalRecordRepository;
import com.petconnect.api.medicalrecord.web.MedicalRecordRequest;
import com.petconnect.api.pet.application.PetService;
import com.petconnect.api.shared.error.ApiException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Casos de uso de histórico médico. Toda operação valida, via
 * {@link PetService#get(String, String)}, que o {@code petId} pertence ao
 * tutor do token — pet (ou registro) de outro tutor responde 404.
 */
@Service
public class MedicalRecordService {

    private final MedicalRecordRepository records;
    private final PetService pets;

    public MedicalRecordService(MedicalRecordRepository records, PetService pets) {
        this.records = records;
        this.pets = pets;
    }

    public List<MedicalRecord> list(String tutorId, String petId) {
        pets.get(tutorId, petId);
        return records.findByPetIdOrderByRecordedAtAsc(petId);
    }

    /**
     * Se {@code req.id()} vier preenchido (fluxo do app: anexos sobem antes
     * do registro existir), usa esse valor como {@code _id} do documento em
     * vez de deixar o Mongo gerar um {@code ObjectId}.
     */
    public MedicalRecord create(String tutorId, String petId, MedicalRecordRequest req) {
        pets.get(tutorId, petId);

        String requestedId = trimToNull(req.id());
        if (requestedId != null && records.existsById(requestedId)) {
            throw ApiException.conflict("Já existe um registro com esse id.");
        }

        MedicalRecord r = new MedicalRecord();
        if (requestedId != null) {
            r.setId(requestedId);
        }
        r.setPetId(petId);
        r.setOrigin(MedicalOrigin.TUTOR);
        apply(r, req);
        return records.save(r);
    }

    public MedicalRecord update(String tutorId, String petId, String recordId, MedicalRecordRequest req) {
        pets.get(tutorId, petId);
        MedicalRecord r = ownedOr404(petId, recordId);
        apply(r, req);
        return records.save(r);
    }

    public void delete(String tutorId, String petId, String recordId) {
        pets.get(tutorId, petId);
        records.delete(ownedOr404(petId, recordId));
    }

    private void apply(MedicalRecord r, MedicalRecordRequest req) {
        r.setRecordedAt(req.recordedAt());
        r.setDescription(req.description().trim());
        r.setVeterinarian(trimToNull(req.veterinarian()));
        r.setAttachments(req.attachments() == null ? new ArrayList<>() : new ArrayList<>(req.attachments()));
    }

    private MedicalRecord ownedOr404(String petId, String recordId) {
        MedicalRecord r = records.findById(recordId)
                .orElseThrow(() -> ApiException.notFound("Registro não encontrado."));
        if (!petId.equals(r.getPetId())) {
            throw ApiException.notFound("Registro não encontrado.");
        }
        return r;
    }

    private static String trimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
