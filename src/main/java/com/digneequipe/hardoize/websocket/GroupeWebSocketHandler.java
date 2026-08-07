package com.digneequipe.hardoize.websocket;

import com.digneequipe.hardoize.repositories.GroupeRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Une session WebSocket par appareil connecté, regroupées par groupeUuid.
 * Remplace entièrement le polling HTTP 30s : dès qu'une opération aboutit
 * côté serveur (vente, mouvement, dette...), le résultat est poussé
 * directement à tous les appareils connectés sur ce groupe.
 *
 * Sert aussi de source de vérité unique pour :
 *  - le statut "en ligne / hors ligne" (une session ouverte = en ligne) ;
 *  - le repli automatique en mode solo quand plus aucun vendeur n'est
 *    connecté (remplace l'ancien MultiModeService.deconnecterMembre,
 *    qui dépendait du même heartbeat que le polling supprimé).
 *
 * ⚠️ Cette table de sessions vit en mémoire du process. Si l'app tourne
 * un jour sur plusieurs instances Spring Boot (scaling horizontal), il
 * faudra remplacer ce registre par un relais partagé (ex. Redis Pub/Sub)
 * pour que la diffusion touche les appareils connectés à une AUTRE
 * instance. Tant que Hardoize tourne sur un seul process, ce n'est pas
 * un problème.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GroupeWebSocketHandler extends TextWebSocketHandler {

    private final ObjectMapper objectMapper;
    private final GroupeRepository groupeRepo;

    // groupeUuid -> sessions ouvertes sur ce groupe
    private final Map<String, Set<WebSocketSession>> sessionsParGroupe =
            new ConcurrentHashMap<>();

    // groupeUuid -> (utilisateurId -> nombre de sessions ouvertes)
    // Un même membre peut avoir plusieurs sessions (deux appareils avec
    // le même compte) — on ne le considère hors ligne que quand la
    // DERNIÈRE session se ferme.
    private final Map<String, Map<Long, Integer>> connexionsParMembre =
            new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String groupeUuid = attribut(session, "groupeUuid");
        Long utilisateurId = (Long) session.getAttributes().get("utilisateurId");
        if (groupeUuid == null) {
            fermerSilencieusement(session);
            return;
        }

        sessionsParGroupe
                .computeIfAbsent(groupeUuid, k -> new CopyOnWriteArraySet<>())
                .add(session);

        boolean premiereConnexionDeCeMembre = false;
        if (utilisateurId != null) {
            Map<Long, Integer> compteurs = connexionsParMembre
                    .computeIfAbsent(groupeUuid, k -> new ConcurrentHashMap<>());
            int nouveauCompte = compteurs.merge(utilisateurId, 1, Integer::sum);
            premiereConnexionDeCeMembre = nouveauCompte == 1;
        }

        log.info("WS connecté : groupe={} utilisateur={} (total sessions groupe={})",
                groupeUuid, utilisateurId, sessionsParGroupe.get(groupeUuid).size());

        if (premiereConnexionDeCeMembre) {
            diffuser(groupeUuid, "membre_connexion",
                    Map.of("utilisateurId", utilisateurId, "estConnecte", true));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String groupeUuid = attribut(session, "groupeUuid");
        Long utilisateurId = (Long) session.getAttributes().get("utilisateurId");
        if (groupeUuid == null) return;

        Set<WebSocketSession> sessions = sessionsParGroupe.get(groupeUuid);
        if (sessions != null) {
            sessions.remove(session);
            if (sessions.isEmpty()) sessionsParGroupe.remove(groupeUuid);
        }

        boolean derniereConnexionDeCeMembre = false;
        if (utilisateurId != null) {
            Map<Long, Integer> compteurs = connexionsParMembre.get(groupeUuid);
            if (compteurs != null) {
                int nouveauCompte = compteurs.merge(utilisateurId, -1, Integer::sum);
                if (nouveauCompte <= 0) {
                    compteurs.remove(utilisateurId);
                    derniereConnexionDeCeMembre = true;
                }
                if (compteurs.isEmpty()) connexionsParMembre.remove(groupeUuid);
            }
        }

        log.info("WS déconnecté : groupe={} utilisateur={}", groupeUuid, utilisateurId);

        if (derniereConnexionDeCeMembre) {
            diffuser(groupeUuid, "membre_connexion",
                    Map.of("utilisateurId", utilisateurId, "estConnecte", false));
            verifierRepliSolo(groupeUuid);
        }
    }

    /**
     * Si plus aucun VENDEUR (non-propriétaire) n'a de session ouverte
     * sur ce groupe, on repasse le groupe en mode "solo". Remplace
     * l'ancienne logique de MultiModeService.deconnecterMembre, qui
     * reposait sur le même heartbeat que le polling supprimé.
     */
    private void verifierRepliSolo(String groupeUuid) {
        Set<WebSocketSession> restantes = sessionsParGroupe.get(groupeUuid);
        boolean vendeurEncoreConnecte = restantes != null && restantes.stream()
                .anyMatch(s -> !Boolean.TRUE.equals(
                        s.getAttributes().get("estProprietaire")));
        if (vendeurEncoreConnecte) return;

        groupeRepo.findByUuid(groupeUuid).ifPresent(g -> {
            if ("multi".equals(g.getMode())) {
                g.setMode("solo");
                groupeRepo.save(g);
                log.info("Groupe {} : repli en mode solo (plus aucun vendeur connecté)",
                        groupeUuid);
            }
        });
    }

    /** Utilisé par GroupeService/MultiModeService pour le statut "en ligne" affiché. */
    public boolean estConnecte(String groupeUuid, Long utilisateurId) {
        if (groupeUuid == null || utilisateurId == null) return false;
        Map<Long, Integer> compteurs = connexionsParMembre.get(groupeUuid);
        return compteurs != null && compteurs.getOrDefault(utilisateurId, 0) > 0;
    }

    /**
     * Ferme immédiatement toute session WebSocket ouverte pour cet
     * utilisateur sur ce groupe — utilisé pour la déconnexion forcée
     * par le propriétaire. La fermeture déclenche naturellement
     * afterConnectionClosed (comptage, diffusion, repli solo), pas
     * besoin de dupliquer cette logique ici.
     */
    public void fermerSessionsDeUtilisateur(String groupeUuid, Long utilisateurId) {
        Set<WebSocketSession> sessions = sessionsParGroupe.get(groupeUuid);
        if (sessions == null) return;
        for (WebSocketSession s : sessions) {
            Object uid = s.getAttributes().get("utilisateurId");
            if (utilisateurId.equals(uid)) {
                try {
                    s.close(CloseStatus.NORMAL.withReason("Déconnexion forcée"));
                } catch (IOException ignored) {}
            }
        }
    }

    /** Diffuse un message JSON {type, data} à toutes les sessions d'un groupe. */
    public void diffuser(String groupeUuid, String type, Object data) {
        Set<WebSocketSession> sessions = sessionsParGroupe.get(groupeUuid);
        if (sessions == null || sessions.isEmpty()) return;

        String json;
        try {
            json = objectMapper.writeValueAsString(Map.of("type", type, "data", data));
        } catch (Exception e) {
            log.error("Erreur sérialisation message WS: {}", e.getMessage());
            return;
        }

        TextMessage message = new TextMessage(json);
        for (WebSocketSession s : sessions) {
            try {
                if (s.isOpen()) s.sendMessage(message);
            } catch (IOException e) {
                log.warn("Échec envoi WS à une session (retirée) : {}", e.getMessage());
                sessions.remove(s);
            }
        }
    }

    /**
     * Envoie un message JSON {type, data} uniquement aux sessions d'UN
     * membre précis d'un groupe (peut en avoir plusieurs — deux
     * appareils connectés au même compte). Utilisé pour notifier en
     * direct un changement qui ne concerne que lui (bail prolongé,
     * connexion permanente activée/désactivée, déconnexion forcée)
     * sans déranger le reste du groupe.
     */
    public void envoyerAUtilisateur(
            String groupeUuid, Long utilisateurId, String type, Object data) {
        Set<WebSocketSession> sessions = sessionsParGroupe.get(groupeUuid);
        if (sessions == null || sessions.isEmpty() || utilisateurId == null) return;

        String json;
        try {
            json = objectMapper.writeValueAsString(Map.of("type", type, "data", data));
        } catch (Exception e) {
            log.error("Erreur sérialisation message WS: {}", e.getMessage());
            return;
        }

        TextMessage message = new TextMessage(json);
        for (WebSocketSession s : sessions) {
            if (!utilisateurId.equals(s.getAttributes().get("utilisateurId"))) continue;
            try {
                if (s.isOpen()) s.sendMessage(message);
            } catch (IOException e) {
                log.warn("Échec envoi WS ciblé à une session (retirée) : {}", e.getMessage());
                sessions.remove(s);
            }
        }
    }

    private String attribut(WebSocketSession session, String cle) {
        Object v = session.getAttributes().get(cle);
        return v != null ? v.toString() : null;
    }

    private void fermerSilencieusement(WebSocketSession session) {
        try { session.close(CloseStatus.BAD_DATA); } catch (IOException ignored) {}
    }
}
