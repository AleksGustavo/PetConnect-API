package com.petconnect.api.notification.application;

import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.AndroidNotification;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.Notification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Push pelo Firebase Cloud Messaging, com a mesma conta de serviço do Admin
 * SDK ({@code FirebaseConfig}). Sem a credencial, todo envio falha e a API
 * segue funcionando.
 *
 * <p>No Android, o aviso usa o canal {@link #ANDROID_CHANNEL} (criado pelo
 * app) e a visibilidade privada: na tela bloqueada o sistema pode esconder o
 * texto, conforme a configuração do aparelho.
 */
@Component
public class FcmPushSender implements PushSender {

    private static final Logger log = LoggerFactory.getLogger(FcmPushSender.class);
    private static final String APP_NAME = "petconnect";
    static final String ANDROID_CHANNEL = "qr_avisos";

    @Override
    public Result send(String token, PushMessage message) {
        FirebaseApp app = app();
        if (app == null) {
            log.warn("Push não enviado: Firebase Admin SDK não inicializado.");
            return Result.FAILED;
        }
        Message fcm = Message.builder()
                .setToken(token)
                .setNotification(Notification.builder()
                        .setTitle(message.title())
                        .setBody(message.body())
                        .build())
                .putAllData(message.data())
                .setAndroidConfig(AndroidConfig.builder()
                        .setPriority(AndroidConfig.Priority.HIGH)
                        .setCollapseKey(message.collapseKey())
                        .setNotification(AndroidNotification.builder()
                                .setChannelId(ANDROID_CHANNEL)
                                .setTag(message.collapseKey())
                                .setVisibility(AndroidNotification.Visibility.PRIVATE)
                                .build())
                        .build())
                .build();
        try {
            FirebaseMessaging.getInstance(app).send(fcm);
            return Result.SENT;
        } catch (FirebaseMessagingException e) {
            MessagingErrorCode code = e.getMessagingErrorCode();
            // Sem o token no log: ele identifica o aparelho.
            log.warn("Push falhou: {} ({}).", code, e.getErrorCode());
            if (code == MessagingErrorCode.UNREGISTERED
                    || code == MessagingErrorCode.SENDER_ID_MISMATCH
                    || code == MessagingErrorCode.INVALID_ARGUMENT) {
                return Result.INVALID_TOKEN;
            }
            if (code == MessagingErrorCode.UNAVAILABLE
                    || code == MessagingErrorCode.INTERNAL
                    || code == MessagingErrorCode.QUOTA_EXCEEDED) {
                return Result.RETRYABLE;
            }
            return Result.FAILED;
        } catch (RuntimeException e) {
            log.warn("Push falhou: {}.", e.getClass().getSimpleName());
            return Result.RETRYABLE;
        }
    }

    private static FirebaseApp app() {
        for (FirebaseApp existing : FirebaseApp.getApps()) {
            if (APP_NAME.equals(existing.getName())) {
                return existing;
            }
        }
        return null;
    }
}
