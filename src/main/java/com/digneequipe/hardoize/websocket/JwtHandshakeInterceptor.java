package com.digneequipe.hardoize.websocket;

import com.digneequipe.hardoize.models.MembreGroupe;
import com.digneequipe.hardoize.repositories.GroupeRepository;
import com.digneequipe.hardoize.repositories.MembreGroupeRepository;
import com.digneequipe.hardoize.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.Map;

/**
 * Le WebSocket natif de React Native ne permet pas de fixer des en-têtes
 * HTTP personnalisés à la connexion — le token JWT et le groupeUuid sont
 * donc passés en paramètres de requête :
 *
 *   wss://host/ws/groupe?token=...&groupeUuid=...
 *
 * Ce token voyage donc dans l'URL (visible dans des logs d'accès HTTP
 * classiques, contrairement à un header) — c'est un compromis accepté
 * ici pour la compatibilité RN. Si ça pose problème plus tard, la
 * parade standard est un token d'échange à courte durée de vie dédié
 * au WebSocket (obtenu via un appel HTTPS classique juste avant).
 *
 * Si le token est invalide, ou que l'utilisateur n'est pas membre actif
 * du groupe demandé, la connexion est refusée (426/403 selon le cas)
 * AVANT l'upgrade WebSocket — jamais de session ouverte pour un
 * utilisateur non autorisé.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtHandshakeInterceptor implements HandshakeInterceptor {

    private final JwtService jwtService;
    private final GroupeRepository groupeRepo;
    private final MembreGroupeRepository membreRepo;

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request, ServerHttpResponse response,
            WebSocketHandler wsHandler, Map<String, Object> attributes) {

        if (!(request instanceof ServletServerHttpRequest servletRequest)) {
            return false;
        }

        Map<String, List<String>> params = UriComponentsBuilder
                .fromUri(request.getURI()).build().getQueryParams()
                .entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(
                        Map.Entry::getKey, Map.Entry::getValue));

        String token      = premierParam(params, "token");
        String groupeUuid = premierParam(params, "groupeUuid");

        if (token == null || groupeUuid == null) {
            log.warn("WS refusé : token ou groupeUuid manquant");
            return false;
        }

        if (!jwtService.validerToken(token)) {
            log.warn("WS refusé : token invalide");
            return false;
        }

        String telephone = jwtService.extraireTelephone(token);

        var groupe = groupeRepo.findByUuid(groupeUuid).orElse(null);
        if (groupe == null) {
            log.warn("WS refusé : groupe introuvable ({})", groupeUuid);
            return false;
        }

        MembreGroupe membre = membreRepo
                .findByGroupeIdAndTelephone(groupe.getId(), telephone)
                .orElse(null);
        if (membre == null || !Boolean.TRUE.equals(membre.getEstActif())) {
            log.warn("WS refusé : {} n'est pas membre actif du groupe {}",
                    telephone, groupeUuid);
            return false;
        }

        // Adhésion pas encore validée par le propriétaire : pas d'accès
        // temps réel tant que ce n'est pas approuvé (voir AdhesionService).
        if (!"approuve".equals(membre.getStatutAdhesion())) {
            log.warn("WS refusé : adhésion de {} au groupe {} pas encore approuvée ({})",
                    telephone, groupeUuid, membre.getStatutAdhesion());
            return false;
        }

        // Attributs disponibles ensuite dans la WebSocketSession
        // (session.getAttributes()) côté GroupeWebSocketHandler.
        attributes.put("telephone",   telephone);
        attributes.put("groupeUuid",  groupeUuid);
        attributes.put("membreUuid",  membre.getUuid());
        attributes.put("utilisateurId", membre.getUtilisateur() != null
                ? membre.getUtilisateur().getId() : null);
        attributes.put("estProprietaire", "proprietaire".equals(membre.getRole()));

        return true;
    }

    @Override
    public void afterHandshake(
            ServerHttpRequest request, ServerHttpResponse response,
            WebSocketHandler wsHandler, Exception exception) {
        // Rien à faire ici.
    }

    private String premierParam(Map<String, List<String>> params, String cle) {
        List<String> valeurs = params.get(cle);
        return (valeurs == null || valeurs.isEmpty()) ? null : valeurs.get(0);
    }
}
