package com.petconnect.api.notification;

import com.petconnect.api.location.infrastructure.LocationRepository;
import com.petconnect.api.notification.application.PushSender;
import com.petconnect.api.notification.application.PushSender.PushMessage;
import com.petconnect.api.notification.application.PushSender.Result;
import com.petconnect.api.notification.domain.TutorNotification;
import com.petconnect.api.notification.infrastructure.PetNotificationPreferencesRepository;
import com.petconnect.api.notification.infrastructure.PushDeviceRepository;
import com.petconnect.api.notification.infrastructure.TutorNotificationRepository;
import com.petconnect.api.pet.domain.Pet;
import com.petconnect.api.pet.infrastructure.PetRepository;
import com.petconnect.api.qractivity.infrastructure.QrAccessEventRepository;
import com.petconnect.api.shared.security.FirebaseTokenVerifier;
import com.petconnect.api.shared.security.VerifiedToken;
import com.petconnect.api.shared.web.RateLimiter;
import com.petconnect.api.sharing.application.FirestorePets;
import com.petconnect.api.sharing.application.FirestorePets.FirestorePet;
import com.petconnect.api.sharing.infrastructure.PublicSharingRepository;
import com.petconnect.api.user.domain.User;
import com.petconnect.api.user.infrastructure.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Avisos ao tutor pelo QR (issue #36 do app, fase 4 do épico #102). */
@SpringBootTest
@AutoConfigureMockMvc
class TutorNotificationTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    UserRepository users;
    @Autowired
    PetRepository pets;
    @Autowired
    PublicSharingRepository sharings;
    @Autowired
    LocationRepository locations;
    @Autowired
    QrAccessEventRepository accesses;
    @Autowired
    PushDeviceRepository devices;
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
    @MockitoBean
    PushSender sender;

    private static final String REX = "fs-rex";
    private static final String TOKEN_A = "token-do-aparelho-da-ana";

    private String rexApiId;

    @BeforeEach
    void setUp() {
        users.deleteAll();
        pets.deleteAll();
        sharings.deleteAll();
        locations.deleteAll();
        accesses.deleteAll();
        devices.deleteAll();
        preferences.deleteAll();
        notifications.deleteAll();
        rateLimiter.clear();
        String tutorA = users.save(User.provision("uid-a", "a@ex.com", "Ana", null)).getId();
        users.save(User.provision("uid-b", "b@ex.com", "Bia", null));
        when(verifier.verify(eq("tok-a"))).thenReturn(new VerifiedToken("uid-a", "a@ex.com", "Ana", null));
        when(verifier.verify(eq("tok-b"))).thenReturn(new VerifiedToken("uid-b", "b@ex.com", "Bia", null));
        when(firestore.find(anyString())).thenReturn(Optional.empty());
        when(firestore.find(REX)).thenReturn(Optional.of(
                new FirestorePet("uid-a", "Rex", "Cachorro", null, null, null, false)));
        when(sender.send(anyString(), any())).thenReturn(Result.SENT);

        Pet rex = new Pet();
        rex.setTutorId(tutorA);
        rex.setName("Rex");
        rex.setLegacyFirestoreId(REX);
        rex.setLegacyImport(true);
        rex.setPublicId(publicIdDe(REX));
        rexApiId = pets.save(rex).getId();
    }

    private static String publicIdDe(String firestoreId) {
        return UUID.nameUUIDFromBytes(("pet:" + firestoreId).getBytes(StandardCharsets.UTF_8)).toString();
    }

    private ResultActions registrar(String token, String deviceToken) throws Exception {
        return mvc.perform(put("/api/v1/me/push-devices")
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content("{\"token\":\"" + deviceToken + "\",\"platform\":\"android\"}"));
    }

    private void abrirPagina() throws Exception {
        mvc.perform(post("/api/v1/public/pets/" + publicIdDe(REX) + "/events")
                        .contentType("application/json").content("{\"type\":\"qr_page_opened\"}"))
                .andExpect(status().isAccepted());
    }

    private void relatar(String json) throws Exception {
        mvc.perform(post("/api/v1/public/pets/" + publicIdDe(REX) + "/sightings")
                        .contentType("application/json").content(json))
                .andExpect(status().isCreated());
    }

    private PushMessage unicoAviso() {
        ArgumentCaptor<PushMessage> captor = ArgumentCaptor.forClass(PushMessage.class);
        verify(sender, times(1)).send(eq(TOKEN_A), captor.capture());
        return captor.getValue();
    }

    // ------------------------------------------------------------ aparelhos

    @Test
    void registrarOAparelhoExigeLoginEValidaOCorpo() throws Exception {
        mvc.perform(put("/api/v1/me/push-devices").contentType("application/json")
                        .content("{\"token\":\"x\"}"))
                .andExpect(status().isUnauthorized());
        registrar("tok-a", "").andExpect(status().isBadRequest());
        mvc.perform(put("/api/v1/me/push-devices").header("Authorization", "Bearer tok-a")
                        .contentType("application/json").content("{\"token\":\"x\",\"platform\":\"windows\"}"))
                .andExpect(status().isBadRequest());

        registrar("tok-a", TOKEN_A).andExpect(status().isNoContent());
        registrar("tok-a", TOKEN_A).andExpect(status().isNoContent());

        assertThat(devices.findAll()).singleElement()
                .satisfies(d -> assertThat(d.getOwnerUid()).isEqualTo("uid-a"));
    }

    @Test
    void outraContaNoMesmoAparelhoLevaOToken() throws Exception {
        registrar("tok-a", TOKEN_A);
        registrar("tok-b", TOKEN_A);

        assertThat(devices.findAll()).singleElement()
                .satisfies(d -> assertThat(d.getOwnerUid()).isEqualTo("uid-b"));
    }

    @Test
    void soODonoRemoveOToken() throws Exception {
        registrar("tok-a", TOKEN_A);

        mvc.perform(delete("/api/v1/me/push-devices").header("Authorization", "Bearer tok-b")
                        .contentType("application/json").content("{\"token\":\"" + TOKEN_A + "\"}"))
                .andExpect(status().isNoContent());
        assertThat(devices.count()).isEqualTo(1);

        mvc.perform(delete("/api/v1/me/push-devices").header("Authorization", "Bearer tok-a")
                        .contentType("application/json").content("{\"token\":\"" + TOKEN_A + "\"}"))
                .andExpect(status().isNoContent());
        assertThat(devices.count()).isZero();
    }

    // ------------------------------------------------------------ avisos

    @Test
    void acessoAvisaSemDizerQueOPetFoiVisto() throws Exception {
        registrar("tok-a", TOKEN_A);

        abrirPagina();

        PushMessage aviso = unicoAviso();
        assertThat(aviso.title()).isEqualTo("O QR Code de Rex foi acessado");
        assertThat(aviso.body()).contains("não confirma que o pet foi visto");
        assertThat(aviso.data()).containsEntry("type", "qr_access")
                .containsEntry("firestorePetId", REX)
                .containsEntry("apiPetId", rexApiId);
        assertThat(notifications.findAll()).singleElement().satisfies(n -> {
            assertThat(n.getStatus()).isEqualTo(TutorNotification.Status.SENT);
            assertThat(n.getType()).isEqualTo(TutorNotification.Type.QR_ACCESS);
        });
    }

    @Test
    void cadaAcessoAvisaOTutor() throws Exception {
        registrar("tok-a", TOKEN_A);

        abrirPagina();
        abrirPagina();
        abrirPagina();

        verify(sender, times(3)).send(anyString(), any());
        assertThat(accesses.count()).isEqualTo(3);
    }

    @Test
    void acessoSemAparelhoNaoImpedeOAvisoDoProximo() throws Exception {
        abrirPagina();
        registrar("tok-a", TOKEN_A);

        abrirPagina();

        verify(sender, times(1)).send(anyString(), any());
    }

    @Test
    void avistamentoComLocalizacaoAvisaSemOTextoNemOContato() throws Exception {
        registrar("tok-a", TOKEN_A);

        relatar("{\"latitude\":-22.17,\"longitude\":-47.39,\"accuracyMeters\":35,"
                + "\"description\":\"Perto da praça\",\"reporterContact\":\"Carlos 19 0000-0000\"}");

        PushMessage aviso = unicoAviso();
        assertThat(aviso.title()).isEqualTo("Novo avistamento de Rex");
        assertThat(aviso.body()).contains("enviou a localização");
        assertThat(aviso.data()).containsEntry("type", "sighting");
        assertThat(aviso.toString()).doesNotContain("praça").doesNotContain("Carlos").doesNotContain("-22.17");
    }

    @Test
    void relatoRepetidoNaoAvisaDeNovo() throws Exception {
        registrar("tok-a", TOKEN_A);
        String body = "{\"description\":\"Perto da escola\",\"clientRequestId\":\"" + UUID.randomUUID() + "\"}";

        relatar(body);
        relatar(body);

        PushMessage aviso = unicoAviso();
        assertThat(aviso.body()).contains("enviou um relato");
    }

    @Test
    void tutorDesligaOAvisoDeAcessoEContinuaRecebendoAvistamento() throws Exception {
        registrar("tok-a", TOKEN_A);
        mvc.perform(put("/api/v1/legacy-pets/" + REX + "/notification-preferences")
                        .header("Authorization", "Bearer tok-a")
                        .contentType("application/json")
                        .content("{\"notifyOnAccess\":false,\"notifyOnSighting\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notifyOnAccess").value(false));

        abrirPagina();
        verify(sender, never()).send(anyString(), any());

        relatar("{\"description\":\"Vi na rua 7\"}");
        verify(sender, times(1)).send(anyString(), any());
    }

    @Test
    void preferenciasPadraoLigadasESoParaODono() throws Exception {
        mvc.perform(get("/api/v1/legacy-pets/" + REX + "/notification-preferences")
                        .header("Authorization", "Bearer tok-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notifyOnAccess").value(true))
                .andExpect(jsonPath("$.notifyOnSighting").value(true));
        mvc.perform(get("/api/v1/legacy-pets/" + REX + "/notification-preferences")
                        .header("Authorization", "Bearer tok-b"))
                .andExpect(status().isNotFound());
        mvc.perform(put("/api/v1/pets/" + rexApiId + "/notification-preferences")
                        .header("Authorization", "Bearer tok-b")
                        .contentType("application/json")
                        .content("{\"notifyOnAccess\":false,\"notifyOnSighting\":false}"))
                .andExpect(status().isNotFound());
        mvc.perform(put("/api/v1/legacy-pets/" + REX + "/notification-preferences")
                        .header("Authorization", "Bearer tok-a")
                        .contentType("application/json")
                        .content("{\"notifyOnAccess\":false}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void semAparelhoRegistradoFicaRegistradoComoNoDevice() throws Exception {
        abrirPagina();

        verify(sender, never()).send(anyString(), any());
        assertThat(notifications.findAll()).singleElement()
                .satisfies(n -> assertThat(n.getStatus()).isEqualTo(TutorNotification.Status.NO_DEVICE));
    }

    @Test
    void tokenInvalidoEApagado() throws Exception {
        registrar("tok-a", TOKEN_A);
        when(sender.send(eq(TOKEN_A), any())).thenReturn(Result.INVALID_TOKEN);

        abrirPagina();

        assertThat(devices.count()).isZero();
        assertThat(notifications.findAll()).singleElement().satisfies(n -> {
            assertThat(n.getStatus()).isEqualTo(TutorNotification.Status.FAILED);
            assertThat(n.getLastError()).isEqualTo("INVALID_TOKEN");
        });
    }

    @Test
    void falhaTemporariaTentaDeNovoUmaVez() throws Exception {
        registrar("tok-a", TOKEN_A);
        when(sender.send(eq(TOKEN_A), any())).thenReturn(Result.RETRYABLE, Result.SENT);

        abrirPagina();

        verify(sender, times(2)).send(eq(TOKEN_A), any());
        assertThat(notifications.findAll()).singleElement().satisfies(n -> {
            assertThat(n.getStatus()).isEqualTo(TutorNotification.Status.SENT);
            assertThat(n.getAttempts()).isEqualTo(2);
        });
    }

    @Test
    void avisoVaiSoParaOsAparelhosDoDonoAtual() throws Exception {
        registrar("tok-b", "token-da-bia");
        registrar("tok-a", TOKEN_A);

        abrirPagina();

        verify(sender, times(1)).send(eq(TOKEN_A), any());
        verify(sender, never()).send(eq("token-da-bia"), any());
    }

    @Test
    void excluirAContaApagaAparelhosEPreferencias() throws Exception {
        registrar("tok-a", TOKEN_A);
        mvc.perform(put("/api/v1/legacy-pets/" + REX + "/notification-preferences")
                .header("Authorization", "Bearer tok-a")
                .contentType("application/json")
                .content("{\"notifyOnAccess\":false,\"notifyOnSighting\":true}"));
        abrirPagina();
        relatar("{\"description\":\"x\"}");

        mvc.perform(delete("/api/v1/me").header("Authorization", "Bearer tok-a"))
                .andExpect(status().isNoContent());

        assertThat(devices.findAll()).isEmpty();
        assertThat(preferences.findAll()).isEmpty();
        assertThat(notifications.findAll()).isEmpty();
    }
}
