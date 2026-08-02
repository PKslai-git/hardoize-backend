package com.digneequipe.hardoize.services;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * Envoi de notifications push via l'API Expo (https://exp.host). Aucune
 * dépendance/SDK à ajouter côté backend : un simple POST HTTP suffit.
 * Le token de chaque utilisateur (ExponentPushToken[...]) est enregistré
 * via PUT /api/utilisateurs/push-token et stocké sur Utilisateur.
 *
 * Volontairement best-effort : un échec d'envoi (token expiré, appareil
 * hors ligne, désinstallation...) ne doit jamais faire échouer
 * l'opération métier qui déclenche la notification (ex. une demande
 * d'adhésion doit être enregistrée même si la notif au propriétaire
 * échoue) — toutes les erreurs sont donc avalées et journalisées.
 */
@Slf4j
@Service
public class PushNotificationService {

    private static final String EXPO_PUSH_URL = "https://exp.host/--/api/v2/push/send";

    private final RestTemplate restTemplate = new RestTemplate();

    public void envoyer(String expoPushToken, String titre, String corps,
                         Map<String, Object> donnees) {
        if (expoPushToken == null || expoPushToken.isBlank()) return;
        if (!expoPushToken.startsWith("ExponentPushToken")
                && !expoPushToken.startsWith("ExpoPushToken")) {
            log.warn("Token push d'apparence invalide, envoi ignoré: {}", expoPushToken);
            return;
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Accept", "application/json");
            headers.set("Accept-Encoding", "gzip, deflate");

            Map<String, Object> message = Map.of(
                    "to",    expoPushToken,
                    "title", titre,
                    "body",  corps,
                    "data",  donnees != null ? donnees : Map.of(),
                    "sound", "default"
            );

            restTemplate.postForEntity(
                    EXPO_PUSH_URL, new HttpEntity<>(message, headers), String.class);
        } catch (Exception e) {
            // Best-effort : ne jamais remonter l'erreur à l'appelant.
            log.warn("Échec envoi push Expo (ignoré) : {}", e.getMessage());
        }
    }
}
