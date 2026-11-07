package com.petconnect.api.privacy;

import com.petconnect.api.location.domain.Location;
import com.petconnect.api.location.domain.LocationSource;
import com.petconnect.api.location.infrastructure.LocationRepository;
import com.petconnect.api.notification.domain.PetNotificationPreferences;
import com.petconnect.api.notification.domain.TutorNotification;
import com.petconnect.api.notification.infrastructure.PetNotificationPreferencesRepository;
import com.petconnect.api.notification.infrastructure.TutorNotificationRepository;
import com.petconnect.api.pet.domain.Pet;
import com.petconnect.api.pet.infrastructure.PetRepository;
import com.petconnect.api.qractivity.domain.QrAccessEvent;
import com.petconnect.api.qractivity.infrastructure.QrAccessEventRepository;
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
import com.petconnect.api.vaccine.domain.Vaccine;
import com.petconnect.api.vaccine.infrastructure.VaccineRepository;
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
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * O tutor exclui um pet do Firestore no app e a API apaga o que guardava do
 * QR dele (LGPD): página pública, acessos, relatos, preferências e avisos.
 * Também cobre a limpeza no acesso ao QR de pet já excluído, a exclusão de
 * pet da API e a exclusão de conta.
 */
@SpringBootTest
@AutoConfigureMockMvc
class QrDataErasureControllerTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    UserRepository users;
    @Autowired
    PetRepository pets;
    @Autowired
    VaccineRepository vaccines;
    @Autowired
    PublicSharingRepository sharings;
    @Autowired
    ConsentEventRepository consentEvents;
    @Autowired
    QrAccessEventRepository accesses;
    @Autowired
    LocationRepository locations;
    @Autowired
    PetNotificationPreferencesRepository preferences;
    @Autowired
    TutorNotificationRepository notifications;
    @Autowired
    RateLimiter rateLimiter;

    @MockitoBean
    FirebaseTokenVerifier verifier;
    @MockitoBean
    FirestorePets firestore;

    /** Pet do Firestore que só existe lá (a migração não levou ao MongoDB). */
    private static final String LUNA = "fs-luna";
    /** Pet do Firestore que a migração levou ao MongoDB. */
    private static final String REX = "fs-rex";
    /** Pet de outro tutor, que nada aqui pode apagar. */
    private static final String THOR = "fs-thor";

    private String tutorA;

    @BeforeEach
    void setUp() {
        users.deleteAll();
        pets.deleteAll();
        vaccines.deleteAll();
        sharings.deleteAll();
        consentEvents.deleteAll();
        accesses.deleteAll();
        locations.deleteAll();
        preferences.deleteAll();
        notifications.deleteAll();
        rateLimiter.clear();

        tutorA = users.save(User.provision("uid-a", "a@ex.com", "Ana", null)).getId();
        users.save(User.provision("uid-b", "b@ex.com", "Bia", null));
        when(verifier.verify(eq("tok-a"))).thenReturn(new VerifiedToken("uid-a", "a@ex.com", "Ana", null));
        when(verifier.verify(eq("tok-b"))).thenReturn(new VerifiedToken("uid-b", "b@ex.com", "Bia", null));

        when(firestore.find(anyString())).thenReturn(Optional.empty());
        when(firestore.find(LUNA)).thenReturn(Optional.of(
                new FirestorePet("uid-a", "Luna", "Gato", null, null, null, false)));
        when(firestore.find(REX)).thenReturn(Optional.of(
                new FirestorePet("uid-a", "Rex", "Cachorro", null, null, null, false)));
        when(firestore.find(THOR)).thenReturn(Optional.of(
                new FirestorePet("uid-b", "Thor", "Cachorro", null, null, null, false)));

        dadosDoQr(LUNA, "uid-a");
        dadosDoQr(THOR, "uid-b");
    }

    // ------------------------------------------------------------ apoio

    /** Tudo o que a API guarda do QR de um pet do Firestore, como o uso real deixaria. */
    private void dadosDoQr(String firestoreId, String ownerUid) {
        String key = PublicSharing.firestoreKey(firestoreId);
        PublicSharing s = PublicSharing.forFirestorePet(firestoreId, ownerUid, publicIdDe(firestoreId));
        s.publish(EnumSet.of(PublicField.PET_NAME, PublicField.CONTACT_PHONE), null, "+5519999990000",
                "2026-11-02", ownerUid, Instant.now());
        sharings.save(s);
        consentEvents.save(ConsentEvent.of(key, ConsentEvent.Type.ACCEPTED, Set.of(PublicField.PET_NAME),
                "2026-11-02", ownerUid, Instant.now()));
        accesses.save(QrAccessEvent.pageOpened(key, null, firestoreId, Instant.now()));
        accesses.save(QrAccessEvent.pageOpened(key, null, firestoreId, Instant.now()));
        locations.save(relato(firestoreId));
        preferences.save(PetNotificationPreferences.defaults(key, ownerUid));
        notifications.save(TutorNotification.pending(key, ownerUid, TutorNotification.Type.SIGHTING, Instant.now()));
    }

    private static Location relato(String firestoreId) {
        Location l = new Location();
        l.setLegacyPetId(firestoreId);
        l.setSource(LocationSource.PUBLIC_QR);
        l.setReportedAt(Instant.now());
        l.setLatitude(-22.9);
        l.setLongitude(-47.06);
        l.setReporterContact("contato de teste");
        return l;
    }

    private String migrar(String firestoreId, String tutorId) {
        Pet p = new Pet();
        p.setTutorId(tutorId);
        p.setName(firestoreId);
        p.setLegacyFirestoreId(firestoreId);
        p.setLegacyImport(true);
        p.setPublicId(publicIdDe(firestoreId));
        return pets.save(p).getId();
    }

    private static String publicIdDe(String firestoreId) {
        return UUID.nameUUIDFromBytes(("pet:" + firestoreId).getBytes(StandardCharsets.UTF_8)).toString();
    }

    private ResultActions apagar(String token, String firestoreId) throws Exception {
        var req = delete("/api/v1/legacy-pets/" + firestoreId + "/qr-data");
        if (token != null) {
            req = req.header("Authorization", "Bearer " + token);
        }
        return mvc.perform(req);
    }

    private void assertSemDadosDoQr(String firestoreId) {
        String key = PublicSharing.firestoreKey(firestoreId);
        assertThat(sharings.findByPetKey(key)).isEmpty();
        assertThat(accesses.findAll()).noneMatch(e -> key.equals(e.getPetKey()));
        assertThat(locations.findAll()).noneMatch(l -> firestoreId.equals(l.getLegacyPetId()));
        assertThat(preferences.findByPetKey(key)).isEmpty();
        assertThat(notifications.findAll()).noneMatch(n -> key.equals(n.getPetKey()));
    }

    private void assertDadosDoQrIntactos(String firestoreId) {
        String key = PublicSharing.firestoreKey(firestoreId);
        assertThat(sharings.findByPetKey(key)).isPresent();
        assertThat(accesses.findAll()).filteredOn(e -> key.equals(e.getPetKey())).hasSize(2);
        assertThat(locations.findAll()).filteredOn(l -> firestoreId.equals(l.getLegacyPetId())).hasSize(1);
        assertThat(preferences.findByPetKey(key)).isPresent();
        assertThat(notifications.findAll()).filteredOn(n -> key.equals(n.getPetKey())).hasSize(1);
    }

    // ------------------------------------------------------------ rota do tutor

    @Test
    void semTokenRetorna401ENadaSai() throws Exception {
        apagar(null, LUNA).andExpect(status().isUnauthorized());
        assertDadosDoQrIntactos(LUNA);
    }

    @Test
    void donoApagaPaginaAcessosRelatosPreferenciasEAvisosSoDoPet() throws Exception {
        apagar("tok-a", LUNA).andExpect(status().isNoContent());

        assertSemDadosDoQr(LUNA);
        // O pet de outro tutor não é tocado.
        assertDadosDoQrIntactos(THOR);
        // O histórico de consentimento fica (prova do consentimento, sem telefone nem nome).
        assertThat(consentEvents.findByPetKeyOrderByAtAsc(PublicSharing.firestoreKey(LUNA))).hasSize(1);
    }

    @Test
    void repetirAExclusaoNaoFalha() throws Exception {
        apagar("tok-a", LUNA).andExpect(status().isNoContent());
        apagar("tok-a", LUNA).andExpect(status().isNoContent());
        assertSemDadosDoQr(LUNA);
    }

    @Test
    void outroTutorRecebe404ENadaSai() throws Exception {
        apagar("tok-b", LUNA).andExpect(status().isNotFound());
        assertDadosDoQrIntactos(LUNA);
    }

    @Test
    void firestoreForaDoArResponde503ENadaSai() throws Exception {
        when(firestore.find(anyString())).thenThrow(new FirestorePets.Unavailable("teste", null));
        apagar("tok-a", LUNA).andExpect(status().isServiceUnavailable());
        assertDadosDoQrIntactos(LUNA);
    }

    @Test
    void petMigradoApagaTambemACopiaNoMongoComVacinasELocalizacoes() throws Exception {
        String rexApiId = migrar(REX, tutorA);
        dadosDoQr(REX, "uid-a");
        Vaccine v = new Vaccine();
        v.setPetId(rexApiId);
        vaccines.save(v);
        Location doTutor = new Location();
        doTutor.setPetId(rexApiId);
        doTutor.setSource(LocationSource.TUTOR);
        doTutor.setReportedAt(Instant.now());
        locations.save(doTutor);

        apagar("tok-a", REX).andExpect(status().isNoContent());

        assertSemDadosDoQr(REX);
        assertThat(pets.findById(rexApiId)).isEmpty();
        assertThat(vaccines.findAll()).noneMatch(x -> rexApiId.equals(x.getPetId()));
        assertThat(locations.findAll()).noneMatch(l -> rexApiId.equals(l.getPetId()));
    }

    @Test
    void copiaMigradaDeOutroTutorNaoEApagada() throws Exception {
        String bia = users.findByFirebaseUid("uid-b").orElseThrow().getId();
        String rexApiId = migrar(REX, bia);

        apagar("tok-a", REX).andExpect(status().isNoContent());

        assertThat(pets.findById(rexApiId)).isPresent();
    }

    // ------------------------------------------------------------ pet já fora do Firestore

    @Test
    void petJaExcluidoDoFirestorePodeSerLimpoDepoisPeloDono() throws Exception {
        // A API estava fora na hora da exclusão; o app tenta de novo depois.
        when(firestore.find(LUNA)).thenReturn(Optional.empty());

        apagar("tok-a", LUNA).andExpect(status().isNoContent());

        assertSemDadosDoQr(LUNA);
    }

    @Test
    void petJaExcluidoDoFirestoreDeOutroDonoRecebe404() throws Exception {
        when(firestore.find(LUNA)).thenReturn(Optional.empty());

        apagar("tok-b", LUNA).andExpect(status().isNotFound());

        assertDadosDoQrIntactos(LUNA);
    }

    @Test
    void petInexistenteSemNadaGuardadoResponde204() throws Exception {
        apagar("tok-a", "fs-inventado").andExpect(status().isNoContent());
        assertDadosDoQrIntactos(LUNA);
        assertDadosDoQrIntactos(THOR);
    }

    // ------------------------------------------------------------ outros caminhos de exclusão

    @Test
    void acessoAoQrDePetExcluidoApagaTodosOsDadosDoQr() throws Exception {
        // O app não conseguiu falar com a API na exclusão e não tentou de novo.
        when(firestore.find(LUNA)).thenReturn(Optional.empty());

        mvc.perform(post("/api/v1/public/pets/" + publicIdDe(LUNA) + "/events")
                        .contentType("application/json").content("{\"type\":\"qr_page_opened\"}"))
                .andExpect(status().isNotFound());

        assertSemDadosDoQr(LUNA);
        assertDadosDoQrIntactos(THOR);
    }

    @Test
    void excluirAContaApagaOsDadosDoQrDosPetsDoFirestore() throws Exception {
        mvc.perform(delete("/api/v1/me").header("Authorization", "Bearer tok-a"))
                .andExpect(status().isNoContent());

        // Acessos e relatos não guardam o dono: saem pelas chaves dos registros que guardam.
        assertSemDadosDoQr(LUNA);
        assertDadosDoQrIntactos(THOR);
    }

    @Test
    void excluirPetDaApiApagaTambemAvisosERelatosPeloIdDoFirestore() throws Exception {
        String rexApiId = migrar(REX, tutorA);
        dadosDoQr(REX, "uid-a");
        String apiKey = PublicSharing.apiKey(rexApiId);
        notifications.save(TutorNotification.pending(apiKey, "uid-a", TutorNotification.Type.QR_ACCESS,
                Instant.now()));

        mvc.perform(delete("/api/v1/pets/" + rexApiId).header("Authorization", "Bearer tok-a"))
                .andExpect(status().isNoContent());

        assertSemDadosDoQr(REX);
        assertThat(notifications.findAll()).noneMatch(n -> apiKey.equals(n.getPetKey()));
        assertDadosDoQrIntactos(LUNA);
    }
}
