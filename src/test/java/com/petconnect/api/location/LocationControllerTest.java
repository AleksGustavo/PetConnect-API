package com.petconnect.api.location;

import com.petconnect.api.location.infrastructure.LocationRepository;
import com.petconnect.api.pet.domain.Pet;
import com.petconnect.api.pet.infrastructure.PetRepository;
import com.petconnect.api.shared.security.FirebaseTokenVerifier;
import com.petconnect.api.shared.security.VerifiedToken;
import com.petconnect.api.user.domain.User;
import com.petconnect.api.user.infrastructure.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Avistamentos do próprio tutor (RF32) — autenticado, escopado por posse do pet. */
@SpringBootTest
@AutoConfigureMockMvc
class LocationControllerTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    UserRepository users;
    @Autowired
    PetRepository pets;
    @Autowired
    LocationRepository locations;

    @MockitoBean
    FirebaseTokenVerifier verifier;

    private String petA;
    private String petB;

    @BeforeEach
    void setUp() {
        users.deleteAll();
        pets.deleteAll();
        locations.deleteAll();
        String tutorA = users.save(User.provision("uid-a", "a@ex.com", "Ana", null)).getId();
        String tutorB = users.save(User.provision("uid-b", "b@ex.com", "Bia", null)).getId();
        petA = savePet(tutorA, "Rex");
        petB = savePet(tutorB, "Bidu");
        when(verifier.verify(eq("tok-a"))).thenReturn(new VerifiedToken("uid-a", "a@ex.com", "Ana", null));
        when(verifier.verify(eq("tok-b"))).thenReturn(new VerifiedToken("uid-b", "b@ex.com", "Bia", null));
    }

    private String savePet(String tutorId, String name) {
        Pet p = new Pet();
        p.setTutorId(tutorId);
        p.setName(name);
        return pets.save(p).getId();
    }

    private String base(String petId) {
        return "/api/v1/pets/" + petId + "/locations";
    }

    @Test
    void semTokenRetorna401() throws Exception {
        mvc.perform(get(base(petA))).andExpect(status().isUnauthorized());
    }

    @Test
    void registraEListaAvistamentoDoProprioPet() throws Exception {
        mvc.perform(post(base(petA)).header("Authorization", "Bearer tok-a")
                        .contentType("application/json")
                        .content("{\"description\":\"Visto perto de casa\",\"reporterContact\":\"19999998888\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.source").value("TUTOR"))
                .andExpect(jsonPath("$.petId").value(petA));

        mvc.perform(get(base(petA)).header("Authorization", "Bearer tok-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].description").value("Visto perto de casa"));
    }

    @Test
    void permiteRegistrarComDataPassadaEscolhidaPeloTutor() throws Exception {
        mvc.perform(post(base(petA)).header("Authorization", "Bearer tok-a")
                        .contentType("application/json")
                        .content("{\"reportedAt\":\"2024-12-01\",\"description\":\"Visto há um tempo\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reportedAt").value(org.hamcrest.Matchers.startsWith("2024-12-01")));
    }

    @Test
    void veOsAvistamentosMigradosDoPet() throws Exception {
        com.petconnect.api.location.domain.Location legacy = new com.petconnect.api.location.domain.Location();
        legacy.setPetId(petA);
        legacy.setLatitude(-22.17);
        legacy.setLongitude(-47.39);
        legacy.setLegacyImport(true);
        locations.save(legacy);

        mvc.perform(get(base(petA)).header("Authorization", "Bearer tok-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].legacyImport").value(true));
    }

    @Test
    void naoAcessaAvistamentosDePetDeOutroTutor() throws Exception {
        mvc.perform(get(base(petB)).header("Authorization", "Bearer tok-a"))
                .andExpect(status().isNotFound());
        mvc.perform(post(base(petB)).header("Authorization", "Bearer tok-a")
                        .contentType("application/json").content("{}"))
                .andExpect(status().isNotFound());

        assertThat(locations.findByPetIdOrderByReportedAtDesc(petB)).isEmpty();
    }
}
