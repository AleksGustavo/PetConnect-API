package com.petconnect.api.shared.config;

import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.Nullable;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

/**
 * Inicializa o Firebase Admin SDK a partir de {@code petconnect.firebase.service-account}
 * (caminho de arquivo OU JSON em base64). Se estiver vazio, o bean {@link FirebaseAuth}
 * é {@code null} (null bean) — a API sobe, mas sem validação de token: rotas
 * autenticadas respondem 401. Mantém dev/CI funcionais sem a credencial.
 */
@Configuration
public class FirebaseConfig {

    private static final Logger log = LoggerFactory.getLogger(FirebaseConfig.class);
    private static final String APP_NAME = "petconnect";

    @Bean
    @Nullable
    FirebaseAuth firebaseAuth(FirebaseProperties props) throws IOException {
        FirebaseApp app = resolveApp(props);
        if (app == null) {
            log.warn("petconnect.firebase.service-account vazio — Firebase Admin SDK NÃO inicializado. "
                    + "Rotas autenticadas responderão 401 até configurar a credencial.");
            return null;
        }
        return FirebaseAuth.getInstance(app);
    }

    @Nullable
    private static FirebaseApp resolveApp(FirebaseProperties props) throws IOException {
        for (FirebaseApp existing : FirebaseApp.getApps()) {
            if (APP_NAME.equals(existing.getName())) {
                return existing;
            }
        }
        if (!props.hasCredentials()) {
            return null;
        }
        try (InputStream credentials = openCredentials(props.serviceAccount())) {
            FirebaseOptions.Builder options = FirebaseOptions.builder()
                    // Transporte HTTP bloqueante (HttpURLConnection). O transporte
                    // assíncrono padrão (ApacheHttp2Transport) abre um NIO Selector,
                    // que falha nesta máquina ("Unable to establish loopback connection").
                    .setHttpTransport(new NetHttpTransport())
                    .setCredentials(GoogleCredentials.fromStream(credentials));
            if (props.projectId() != null && !props.projectId().isBlank()) {
                options.setProjectId(props.projectId());
            }
            FirebaseApp app = FirebaseApp.initializeApp(options.build(), APP_NAME);
            log.info("Firebase Admin SDK inicializado (projeto {}).", app.getOptions().getProjectId());
            return app;
        }
    }

    /** Aceita tanto um caminho de arquivo quanto o JSON inteiro em base64. */
    private static InputStream openCredentials(String value) throws IOException {
        String trimmed = value.trim();
        Path asPath = Path.of(trimmed);
        if (Files.isRegularFile(asPath)) {
            return Files.newInputStream(asPath);
        }
        try {
            return new ByteArrayInputStream(Base64.getDecoder().decode(trimmed));
        } catch (IllegalArgumentException notBase64) {
            return new ByteArrayInputStream(trimmed.getBytes(StandardCharsets.UTF_8));
        }
    }
}
