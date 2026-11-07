package com.petconnect.api.location;

import com.petconnect.api.location.domain.LocationSource;
import com.petconnect.api.location.infrastructure.LocationRepository;
import com.petconnect.api.pet.domain.Pet;
import com.petconnect.api.pet.domain.PetStatus;
import com.petconnect.api.pet.domain.Species;
import com.petconnect.api.pet.infrastructure.PetRepository;
import com.petconnect.api.sharing.application.PublicSharingService;
import com.petconnect.api.sharing.domain.PublicField;
import com.petconnect.api.sharing.domain.PublicSharing;
import com.petconnect.api.sharing.infrastructure.PublicSharingRepository;
import com.petconnect.api.user.domain.User;
import com.petconnect.api.user.infrastructure.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Endpoints públicos (RF17-19, RF31) — sem {@code Authorization}, nunca
 * devem exigir token nem vazar dado do tutor. Os casos de consentimento de
 * pets do Firestore estão em {@code PublicSharingControllerTest}.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PublicPetControllerTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    UserRepository users;
    @Autowired
    PetRepository pets;
    @Autowired
    LocationRepository locations;
    @Autowired
    PublicSharingRepository sharings;

    private String activePublicId;
    private String activePetId;

    @BeforeEach
    void setUp() {
        users.deleteAll();
        pets.deleteAll();
        locations.deleteAll();
        sharings.deleteAll();
        String tutorId = users.save(User.provision("uid-a", "a@ex.com", "Ana", null)).getId();

        Pet active = new Pet();
        active.setTutorId(tutorId);
        active.setName("Rex");
        active.setSpecies(Species.DOG);
        active.setStatus(PetStatus.LOST);
        active.setPhotoUrl("https://res.cloudinary.com/x/rex.jpg");
        active.setPublicContactPhone("19999990000");
        Pet savedActive = pets.save(active);
        activePetId = savedActive.getId();
        activePublicId = savedActive.getPublicId();

        Pet archived = new Pet();
        archived.setTutorId(tutorId);
        archived.setName("Bidu");
        archived.setStatus(PetStatus.ARCHIVED);
        pets.save(archived);
    }

    @Test
    void semConsentimentoOResumoPublicoEOMinimoNeutro() throws Exception {
        // Nome, espécie, situação, foto e telefone existem no banco, mas o
        // tutor não autorizou nada (issue #35 do app).
        mvc.perform(get("/api/v1/public/pets/" + activePublicId))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"shared\":false}", true));
    }

    @Test
    void comConsentimentoMostraSoOsCamposMarcadosDoPetDaApi() throws Exception {
        PublicSharing s = PublicSharing.forApiPet(activePetId, "uid-a");
        s.publish(EnumSet.of(PublicField.PET_NAME, PublicField.SPECIES), null, null, List.of(),
                PublicSharingService.CONSENT_VERSION, "uid-a", Instant.now());
        sharings.save(s);

        mvc.perform(get("/api/v1/public/pets/" + activePublicId))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"shared\":true,\"name\":\"Rex\",\"species\":\"DOG\"}", true));
    }

    @Test
    void tokenInvalidoNaoBloqueiaORecursoPublico() throws Exception {
        mvc.perform(get("/api/v1/public/pets/" + activePublicId)
                        .header("Authorization", "Bearer isto-nao-e-um-token-valido"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shared").value(false));
    }

    @Test
    void publicIdInexistenteRetorna404() throws Exception {
        mvc.perform(get("/api/v1/public/pets/nao-existe"))
                .andExpect(status().isNotFound());
    }

    @Test
    void petArquivadoNaoAparecePublicamente() throws Exception {
        Pet arquivado = pets.findAll().stream()
                .filter(p -> p.getStatus() == PetStatus.ARCHIVED).findFirst().orElseThrow();
        mvc.perform(get("/api/v1/public/pets/" + arquivado.getPublicId()))
                .andExpect(status().isNotFound());
    }

    @Test
    void relatoAnonimoDeAvistamentoSemToken() throws Exception {
        mvc.perform(post("/api/v1/public/pets/" + activePublicId + "/sightings")
                        .contentType("application/json")
                        .content("{\"latitude\":-22.17,\"longitude\":-47.39,\"description\":\"Visto no parque\"}"))
                .andExpect(status().isCreated())
                // Sem corpo: nada de id interno nem eco do que foi enviado.
                .andExpect(content().string(""));

        assertThat(locations.findByPetIdOrderByReportedAtDesc(activePetId))
                .singleElement()
                .satisfies(l -> assertThat(l.getSource()).isEqualTo(LocationSource.PUBLIC_QR));
    }

    @Test
    void relatoParaPublicIdInexistenteRetorna404() throws Exception {
        mvc.perform(post("/api/v1/public/pets/nao-existe/sightings")
                        .contentType("application/json").content("{}"))
                .andExpect(status().isNotFound());
    }
}
