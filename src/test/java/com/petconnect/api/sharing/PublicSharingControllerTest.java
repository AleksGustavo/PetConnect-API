package com.petconnect.api.sharing;

import com.petconnect.api.location.domain.Location;
import com.petconnect.api.location.infrastructure.LocationRepository;
import com.petconnect.api.pet.domain.Pet;
import com.petconnect.api.pet.infrastructure.PetRepository;
import com.petconnect.api.shared.security.FirebaseTokenVerifier;
import com.petconnect.api.shared.security.VerifiedToken;
import com.petconnect.api.shared.web.RateLimiter;
import com.petconnect.api.sharing.application.FirestorePets;
import com.petconnect.api.sharing.application.FirestorePets.FirestorePet;
import com.petconnect.api.sharing.domain.ConsentEvent;
import com.petconnect.api.sharing.domain.PublicField;
import com.petconnect.api.sharing.domain.PublicSharing;
import com.petconnect.api.sharing.infrastructure.ConsentEventRepository;
import com.petconnect.api.sharing.infrastructure.PublicSharingRepository;
import com.petconnect.api.user.domain.User;
import com.petconnect.api.user.infrastructure.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Página pública com consentimento (issue #35 do app, fase 1 do épico #102). */
@SpringBootTest
@AutoConfigureMockMvc
class PublicSharingControllerTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    UserRepository users;
    @Autowired
    PetRepository pets;
    @Autowired
    PublicSharingRepository sharings;
    @Autowired
    ConsentEventRepository events;
    @Autowired
    LocationRepository locations;
    @Autowired
    RateLimiter rateLimiter;

    @MockitoBean
    FirebaseTokenVerifier verifier;
    @MockitoBean
    FirestorePets firestore;

    /** Pet do Firestore que a migração já levou ao MongoDB. */
    private static final String REX = "fs-rex";
    /** Pet do Firestore criado depois da última migração: só existe lá. */
    private static final String NOVO = "fs-novo";
    private static final String MEMORIAL = "fs-memorial";
    private static final String VERSAO = "2026-11-02";
    private static final String FOTO = "https://res.cloudinary.com/qgrx3f8s/image/upload/v1/rex.jpg";

    @BeforeEach
    void setUp() {
        users.deleteAll();
        pets.deleteAll();
        sharings.deleteAll();
        events.deleteAll();
        locations.deleteAll();
        rateLimiter.clear();
        String tutorA = users.save(User.provision("uid-a", "a@ex.com", "Ana", null)).getId();
        users.save(User.provision("uid-b", "b@ex.com", "Bia", null));
        when(verifier.verify(eq("tok-a"))).thenReturn(new VerifiedToken("uid-a", "a@ex.com", "Ana", null));
        when(verifier.verify(eq("tok-b"))).thenReturn(new VerifiedToken("uid-b", "b@ex.com", "Bia", null));

        when(firestore.find(anyString())).thenReturn(Optional.empty());
        when(firestore.find(REX)).thenReturn(Optional.of(
                new FirestorePet("uid-a", "Rex", "Cachorro", "SRD", "Caramelo", FOTO, false)));
        when(firestore.find(NOVO)).thenReturn(Optional.of(
                new FirestorePet("uid-a", "Luna", "Gato", null, "Preta", null, false)));
        when(firestore.find(MEMORIAL)).thenReturn(Optional.of(
                new FirestorePet("uid-a", "Bidu", "Cachorro", null, null, null, true)));

        pets.save(migrado(tutorA, REX, "Rex (cópia antiga)"));
        pets.save(migrado(tutorA, MEMORIAL, "Bidu"));
    }

    private static Pet migrado(String tutorId, String firestoreId, String nome) {
        Pet p = new Pet();
        p.setTutorId(tutorId);
        p.setName(nome);
        p.setLegacyFirestoreId(firestoreId);
        p.setLegacyImport(true);
        p.setPublicContactPhone("19911112222");
        p.setPublicId(publicIdDe(firestoreId));
        return p;
    }

    private static String publicIdDe(String firestoreId) {
        return UUID.nameUUIDFromBytes(("pet:" + firestoreId).getBytes(StandardCharsets.UTF_8)).toString();
    }

    private static String rota(String firestoreId) {
        return "/api/v1/legacy-pets/" + firestoreId + "/public-sharing";
    }

    private ResultActions salvar(String token, String firestoreId, String json) throws Exception {
        return mvc.perform(put(rota(firestoreId))
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content(json));
    }

    private static String publicar(String campos, String extra) {
        return "{\"enabled\":true,\"consentAccepted\":true,\"consentVersion\":\"" + VERSAO + "\","
                + "\"fields\":[" + campos + "]" + extra + "}";
    }

    private ResultActions paginaPublica(String publicId) throws Exception {
        return mvc.perform(get("/api/v1/public/pets/" + publicId));
    }

    @Test
    void semTokenRetorna401() throws Exception {
        mvc.perform(get(rota(REX))).andExpect(status().isUnauthorized());
        mvc.perform(put(rota(REX)).contentType("application/json").content("{\"enabled\":false}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void semConfiguracaoComecaDesligadoEPaginaMostraSoOMinimoNeutro() throws Exception {
        mvc.perform(get(rota(REX)).header("Authorization", "Bearer tok-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.fields").isEmpty())
                .andExpect(jsonPath("$.currentConsentVersion").value(VERSAO));

        // O pet migrado tem nome e telefone no MongoDB, mas nada sai sem consentimento.
        paginaPublica(publicIdDe(REX))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"shared\":false}", true));
    }

    @Test
    void outroTutorNaoLeNemAlteraARecebe404() throws Exception {
        mvc.perform(get(rota(REX)).header("Authorization", "Bearer tok-b")).andExpect(status().isNotFound());
        salvar("tok-b", REX, publicar("\"PET_NAME\"", "")).andExpect(status().isNotFound());
        salvar("tok-b", REX, "{\"enabled\":false}").andExpect(status().isNotFound());
        assertThat(sharings.findAll()).isEmpty();
    }

    @Test
    void naoPublicaSemConsentimentoSemCamposOuNaVersaoErradaDoTexto() throws Exception {
        salvar("tok-a", REX, "{\"enabled\":true,\"consentVersion\":\"" + VERSAO + "\",\"fields\":[\"PET_NAME\"]}")
                .andExpect(status().isBadRequest());
        salvar("tok-a", REX, "{\"enabled\":true,\"consentAccepted\":false,\"consentVersion\":\"" + VERSAO
                + "\",\"fields\":[\"PET_NAME\"]}")
                .andExpect(status().isBadRequest());
        salvar("tok-a", REX, publicar("", "")).andExpect(status().isBadRequest());
        salvar("tok-a", REX, "{\"enabled\":true,\"consentAccepted\":true,\"consentVersion\":\"2020-01-01\","
                + "\"fields\":[\"PET_NAME\"]}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONSENT_VERSION_OUTDATED"));
        salvar("tok-a", REX, publicar("\"CONTACT_PHONE\"", ",\"contactPhone\":\"123\""))
                .andExpect(status().isBadRequest());
        salvar("tok-a", REX, publicar("\"TUTOR_NAME\"", ",\"tutorDisplayName\":\"  \""))
                .andExpect(status().isBadRequest());
        salvar("tok-a", REX, publicar("\"CAMPO_INVENTADO\"", "")).andExpect(status().isBadRequest());

        assertThat(sharings.findAll()).isEmpty();
        paginaPublica(publicIdDe(REX)).andExpect(content().json("{\"shared\":false}", true));
    }

    @Test
    void paginaMostraSoOsCamposMarcadosComOsDadosAtuaisDoFirestore() throws Exception {
        salvar("tok-a", REX, publicar("\"PET_NAME\",\"PROFILE_PHOTO\"", ""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.consentVersion").value(VERSAO))
                .andExpect(jsonPath("$.consentAcceptedAt").exists());

        paginaPublica(publicIdDe(REX))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().json("{\"shared\":true,\"name\":\"Rex\",\"photoUrl\":\"" + FOTO + "\"}", true));
    }

    @Test
    void telefoneENomeDoTutorSoComAsCaixasMarcadas() throws Exception {
        salvar("tok-a", REX, publicar("\"SPECIES\",\"BREED\",\"COLOR\",\"TUTOR_NAME\",\"CONTACT_PHONE\"",
                ",\"tutorDisplayName\":\" Ana  Paula \",\"contactPhone\":\"(19) 99999-0000\""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contactPhone").value("+5519999990000"))
                .andExpect(jsonPath("$.tutorDisplayName").value("Ana Paula"));

        paginaPublica(publicIdDe(REX))
                .andExpect(content().json("{\"shared\":true,\"species\":\"Cachorro\",\"breed\":\"SRD\","
                        + "\"color\":\"Caramelo\",\"tutorName\":\"Ana Paula\","
                        + "\"publicContactPhone\":\"+5519999990000\"}", true));

        // Desmarcar o telefone tira o número da resposta e do banco.
        salvar("tok-a", REX, publicar("\"SPECIES\"", ",\"contactPhone\":\"(19) 99999-0000\""))
                .andExpect(jsonPath("$.contactPhone").doesNotExist());
        paginaPublica(publicIdDe(REX)).andExpect(content().json("{\"shared\":true,\"species\":\"Cachorro\"}", true));
        assertThat(sharings.findByPetKey("fs:" + REX).orElseThrow().getContactPhoneE164()).isNull();
    }

    @Test
    void revogarValeNoAcessoSeguinteEApagaTelefoneENome() throws Exception {
        salvar("tok-a", REX, publicar("\"PET_NAME\",\"TUTOR_NAME\",\"CONTACT_PHONE\"",
                ",\"tutorDisplayName\":\"Ana\",\"contactPhone\":\"+55 19 99999-0000\"")).andExpect(status().isOk());
        paginaPublica(publicIdDe(REX)).andExpect(jsonPath("$.name").value("Rex"));

        salvar("tok-a", REX, "{\"enabled\":false}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.consentRevokedAt").exists())
                .andExpect(jsonPath("$.contactPhone").doesNotExist())
                .andExpect(jsonPath("$.tutorDisplayName").doesNotExist());

        paginaPublica(publicIdDe(REX)).andExpect(content().json("{\"shared\":false}", true));

        List<ConsentEvent> historico = events.findByPetKeyOrderByAtAsc("fs:" + REX);
        assertThat(historico).extracting(ConsentEvent::getType)
                .containsExactly(ConsentEvent.Type.ACCEPTED, ConsentEvent.Type.REVOKED);
        assertThat(historico.get(0).getFields())
                .containsExactlyInAnyOrder(PublicField.PET_NAME, PublicField.TUTOR_NAME, PublicField.CONTACT_PHONE);
        assertThat(historico.get(0).getActorUid()).isEqualTo("uid-a");
    }

    @Test
    void petAindaNaoMigradoPublicaEOQrDeleAbreAPagina() throws Exception {
        paginaPublica(publicIdDe(NOVO)).andExpect(status().isNotFound());

        salvar("tok-a", NOVO, publicar("\"PET_NAME\",\"COLOR\"", "")).andExpect(status().isOk());

        paginaPublica(publicIdDe(NOVO))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"shared\":true,\"name\":\"Luna\",\"color\":\"Preta\"}", true));

        mvc.perform(post("/api/v1/public/pets/" + publicIdDe(NOVO) + "/sightings")
                        .contentType("application/json")
                        .content("{\"description\":\"Perto da praça\"}"))
                .andExpect(status().isCreated())
                .andExpect(content().string(""));
        Location relato = locations.findAll().get(0);
        assertThat(relato.getPetId()).isNull();
        assertThat(relato.getLegacyPetId()).isEqualTo(NOVO);
    }

    @Test
    void qrRegeneradoRevogaOIdDeterministicoMesmoComPublicacao() throws Exception {
        salvar("tok-a", REX, publicar("\"PET_NAME\"", "")).andExpect(status().isOk());
        Pet rex = pets.findByLegacyFirestoreId(REX).orElseThrow();
        rex.setPublicId("novo-qr-aleatorio");
        pets.save(rex);

        paginaPublica(publicIdDe(REX)).andExpect(status().isNotFound());
        paginaPublica("novo-qr-aleatorio").andExpect(jsonPath("$.name").value("Rex"));
    }

    @Test
    void petNoMemorialNaoTemPaginaPublica() throws Exception {
        salvar("tok-a", MEMORIAL, publicar("\"PET_NAME\"", ""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PET_IN_MEMORIAL"));
        salvar("tok-a", MEMORIAL, "{\"enabled\":false}").andExpect(status().isOk());
        paginaPublica(publicIdDe(MEMORIAL)).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/public/pets/" + publicIdDe(MEMORIAL) + "/sightings")
                        .contentType("application/json").content("{}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void petExcluidoDoFirestoreSomeDaPagina() throws Exception {
        salvar("tok-a", REX, publicar("\"PET_NAME\"", "")).andExpect(status().isOk());
        when(firestore.find(REX)).thenReturn(Optional.empty());
        paginaPublica(publicIdDe(REX)).andExpect(status().isNotFound());
        // A configuração (com telefone) não fica para trás.
        assertThat(sharings.findByPetKey("fs:" + REX)).isEmpty();
    }

    @Test
    void publicacaoEmVersaoAntigaDoTextoNaoValeMais() throws Exception {
        PublicSharing antiga = PublicSharing.forFirestorePet(REX, "uid-a", publicIdDe(REX));
        antiga.publish(EnumSet.of(PublicField.PET_NAME), null, null, "2020-01-01", "uid-a", Instant.now());
        sharings.save(antiga);

        paginaPublica(publicIdDe(REX)).andExpect(content().json("{\"shared\":false}", true));
        mvc.perform(get(rota(REX)).header("Authorization", "Bearer tok-a"))
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void firestoreForaDoArNaoExpoeNadaNemFingeSucesso() throws Exception {
        salvar("tok-a", REX, publicar("\"PET_NAME\"", "")).andExpect(status().isOk());
        when(firestore.find(anyString())).thenThrow(new FirestorePets.Unavailable("teste", null));

        salvar("tok-a", REX, publicar("\"PET_NAME\"", "")).andExpect(status().isServiceUnavailable());
        // Publicado: sem confirmar o pet no Firestore, não mostra dados.
        paginaPublica(publicIdDe(REX)).andExpect(status().isServiceUnavailable());
        // Sem publicação: o mínimo neutro não depende do Firestore.
        paginaPublica(publicIdDe(MEMORIAL)).andExpect(content().json("{\"shared\":false}", true));
    }

    @Test
    void excluirAContaApagaAsPaginasPublicasComTelefone() throws Exception {
        salvar("tok-a", REX, publicar("\"CONTACT_PHONE\"", ",\"contactPhone\":\"19999990000\"")).andExpect(status().isOk());
        salvar("tok-a", NOVO, publicar("\"CONTACT_PHONE\"", ",\"contactPhone\":\"19999990000\"")).andExpect(status().isOk());
        assertThat(sharings.findAll()).hasSize(2);

        mvc.perform(delete("/api/v1/me").header("Authorization", "Bearer tok-a"))
                .andExpect(status().isNoContent());

        assertThat(sharings.findAll()).isEmpty();
    }

    @Test
    void respostaPublicaNuncaTrazIdInternoEmailOuSituacao() throws Exception {
        salvar("tok-a", REX, publicar("\"PET_NAME\",\"SPECIES\",\"BREED\",\"COLOR\",\"PROFILE_PHOTO\","
                + "\"TUTOR_NAME\",\"CONTACT_PHONE\"", ",\"tutorDisplayName\":\"Ana\",\"contactPhone\":\"19999990000\""))
                .andExpect(status().isOk());
        paginaPublica(publicIdDe(REX))
                .andExpect(jsonPath("$.id").doesNotExist())
                .andExpect(jsonPath("$.publicId").doesNotExist())
                .andExpect(jsonPath("$.tutorId").doesNotExist())
                .andExpect(jsonPath("$.email").doesNotExist())
                .andExpect(jsonPath("$.status").doesNotExist())
                .andExpect(jsonPath("$.firestoreId").doesNotExist());
    }
}
