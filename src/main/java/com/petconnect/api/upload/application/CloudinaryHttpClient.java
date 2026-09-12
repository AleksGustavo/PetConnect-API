package com.petconnect.api.upload.application;

import com.petconnect.api.shared.config.CloudinaryProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Instant;
import java.util.Map;

/** Implementação real de {@link CloudinaryClient} — chama a API do Cloudinary por HTTP. */
@Component
public class CloudinaryHttpClient implements CloudinaryClient {

    private static final Logger log = LoggerFactory.getLogger(CloudinaryHttpClient.class);

    private final CloudinaryProperties props;
    private final RestClient restClient;

    public CloudinaryHttpClient(CloudinaryProperties props) {
        this.props = props;
        // HttpURLConnection por baixo (não NIO) — mesma cautela usada para o Firebase Admin SDK.
        this.restClient = RestClient.builder()
                .requestFactory(new SimpleClientHttpRequestFactory())
                .build();
    }

    @Override
    public boolean destroy(String resourceType, String publicId) {
        long timestamp = Instant.now().getEpochSecond();
        // invalidate=true: sem isto, o Cloudinary só remove o arquivo do storage —
        // a URL antiga continua servível pela CDN até o cache expirar sozinho
        // (visto na prática: Cache-Control immutable, max-age de 30 dias). Achado
        // testando de ponta a ponta contra a conta real, não hipotético.
        String signature = CloudinarySigner.sign(
                Map.of("public_id", publicId, "invalidate", "true", "timestamp", String.valueOf(timestamp)),
                props.apiSecret());

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("public_id", publicId);
        form.add("invalidate", "true");
        form.add("timestamp", String.valueOf(timestamp));
        form.add("api_key", props.apiKey());
        form.add("signature", signature);

        String url = "https://api.cloudinary.com/v1_1/%s/%s/destroy".formatted(props.cloudName(), resourceType);
        try {
            Map<?, ?> response = restClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(Map.class);
            Object result = response == null ? null : response.get("result");
            boolean ok = "ok".equals(result) || "not found".equals(result);
            if (!ok) {
                log.warn("Cloudinary destroy inesperado para {}/{}: {}", resourceType, publicId, response);
            }
            return ok;
        } catch (RestClientException e) {
            log.error("Falha ao chamar Cloudinary destroy ({}/{})", resourceType, publicId, e);
            return false;
        }
    }
}
