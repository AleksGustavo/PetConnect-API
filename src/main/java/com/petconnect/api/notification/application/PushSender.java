package com.petconnect.api.notification.application;

import java.util.Map;

/**
 * Envia um push a um aparelho. A implementação real é o FCM
 * ({@code FcmPushSender}); os testes trocam por um falso.
 */
public interface PushSender {

    Result send(String token, PushMessage message);

    /**
     * @param collapseKey avisos com a mesma chave se substituem no aparelho,
     *                    para três acessos seguidos não virarem três avisos
     */
    record PushMessage(String title, String body, Map<String, String> data, String collapseKey) {
    }

    enum Result {
        SENT,
        /** Token expirado, de outro projeto ou do app desinstalado: apagar. */
        INVALID_TOKEN,
        /** Falha temporária do FCM: vale tentar de novo. */
        RETRYABLE,
        FAILED
    }
}
