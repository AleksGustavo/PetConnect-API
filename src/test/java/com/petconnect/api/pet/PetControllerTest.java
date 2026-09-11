package com.petconnect.api.pet;

import com.petconnect.api.location.domain.Location;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PetControllerTest {

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

    private String tutorAId;
    private String tutorBId;

    @BeforeEach
    void setUp() {
        users.deleteAll();
        pets.deleteAll();
        locations.deleteAll();
        tutorAId = users.save(User.provision("uid-a", "a@ex.com", "Ana", null)).getId();
        tutorBId = users.save(User.provision("uid-b", "b@ex.com", "Bia", null)).getId();
        when(verifier.verify(eq("tok-a")))
                .thenReturn(new VerifiedToken("uid-a", "a@ex.com", "Ana", null));
        when(verifier.verify(eq("tok-b")))
                .thenReturn(new VerifiedToken("uid-b", "b@ex.com", "Bia", null));
    }

    private String bearerA() {
        return "Bearer tok-a";
    }

    @Test
    void semTokenRetorna401() throws Exception {
        mvc.perform(get("/api/v1/pets")).andExpect(status().isUnauthorized());
    }

    @Test
    void listaVaziaNoComeco() throws Exception {
        mvc.perform(get("/api/v1/pets").header("Authorization", bearerA()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void criaListaEDetalhaUmPet() throws Exception {
        String id = criarPet("""
                {"name":"Rex","species":"DOG","gender":"MALE","size":"MEDIUM","weightKg":12.5,
                 "birthDate":"2019-05-01","breed":"SRD","vaccinatedFlag":true}
                """);

        mvc.perform(get("/api/v1/pets").header("Authorization", bearerA()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("Rex"));

        mvc.perform(get("/api/v1/pets/" + id).header("Authorization", bearerA()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.species").value("DOG"))
                .andExpect(jsonPath("$.size").value("MEDIUM"))
                .andExpect(jsonPath("$.weightKg").value(12.5))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.publicId").isNotEmpty());
    }

    @Test
    void patchAtualizaCampos() throws Exception {
        String id = criarPet("{\"name\":\"Rex\"}");

        mvc.perform(patch("/api/v1/pets/" + id)
                        .header("Authorization", bearerA())
                        .contentType("application/json")
                        .content("{\"name\":\"Rex II\",\"status\":\"LOST\",\"weightKg\":15}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Rex II"))
                .andExpect(jsonPath("$.status").value("LOST"))
                .andExpect(jsonPath("$.weightKg").value(15.0));
    }

    @Test
    void deleteRemovePetEmCascataComLocalizacoes() throws Exception {
        String id = criarPet("{\"name\":\"Rex\"}");
        Location loc = new Location();
        loc.setPetId(id);
        locations.save(loc);

        mvc.perform(delete("/api/v1/pets/" + id).header("Authorization", bearerA()))
                .andExpect(status().isNoContent());

        assertThat(pets.findById(id)).isEmpty();
        assertThat(locations.count()).isZero();
    }

    @Test
    void naoEnxergaNemMexeEmPetDeOutroTutor() throws Exception {
        Pet alheio = new Pet();
        alheio.setTutorId(tutorBId);
        alheio.setName("Bidu");
        String outroId = pets.save(alheio).getId();

        mvc.perform(get("/api/v1/pets/" + outroId).header("Authorization", bearerA()))
                .andExpect(status().isNotFound());
        mvc.perform(patch("/api/v1/pets/" + outroId)
                        .header("Authorization", bearerA())
                        .contentType("application/json").content("{\"name\":\"x\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/pets/" + outroId).header("Authorization", bearerA()))
                .andExpect(status().isNotFound());

        mvc.perform(get("/api/v1/pets").header("Authorization", bearerA()))
                .andExpect(jsonPath("$", hasSize(0)));
        assertThat(pets.findById(outroId)).isPresent();
    }

    @Test
    void postSemNomeRetorna400() throws Exception {
        mvc.perform(post("/api/v1/pets")
                        .header("Authorization", bearerA())
                        .contentType("application/json").content("{\"species\":\"DOG\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    private String criarPet(String json) throws Exception {
        String body = mvc.perform(post("/api/v1/pets")
                        .header("Authorization", bearerA())
                        .contentType("application/json").content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return com.jayway.jsonpath.JsonPath.read(body, "$.id");
    }
}
