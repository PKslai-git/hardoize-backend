package com.digneequipe.hardoize.controllers;

import com.digneequipe.hardoize.dto.response.ApiResponse;
import com.digneequipe.hardoize.services.MultiModeService;
import com.digneequipe.hardoize.websocket.GroupeWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Le rejoindre/sync/heartbeat par polling a été retiré d'ici :
 *  - rejoindre               -> AdhesionController (jointure en 2 temps)
 *  - sync/{groupeUuid}       -> supprimé (remplacé par le WebSocket)
 *  - connecter/{membreUuid}  -> supprimé (heartbeat mort, présence via WS)
 *  - deconnecter/{membreUuid}-> supprimé (fermer la connexion WS suffit)
 */
@RestController
@RequestMapping("/api/multi")
@RequiredArgsConstructor
public class MultiModeController {

    private final MultiModeService multiService;
    private final GroupeWebSocketHandler groupeWebSocketHandler;

    // POST /api/multi/operation
    @PostMapping("/operation")
    public ResponseEntity<ApiResponse<Map<String,Object>>> operation(
            @RequestBody Map<String,Object> payload,
            Authentication auth) {
        try {
            Map<String, Object> resultat =
                    multiService.traiterOperation(payload, auth.getName());

            // Diffusion temps réel : à ce stade la transaction est déjà
            // commitée (traiterOperation est @Transactional et vient de
            // retourner), donc les autres appareils qui recevront ce
            // message verraient bien la même donnée. Le message contient
            // directement le résultat — pas besoin pour les autres
            // appareils de refaire un appel HTTP pour l'appliquer (voir
            // appliquerResultatOperation côté frontend).
            Object groupeUuid = payload.get("groupeUuid");
            if (groupeUuid instanceof String gUuid) {
                // "data" (la requête d'origine : clientUuid, typePaiement,
                // produitUuid...) est inclus en plus de "resultat" (la
                // vérité calculée par le serveur : stocks à jour, lignes,
                // montants) — les AUTRES appareils ont besoin des deux
                // pour reconstituer l'enregistrement local (voir
                // appliquerResultatOperation côté frontend, qui prenait
                // déjà les deux en argument pour son propre appel HTTP).
                Object data = payload.getOrDefault("data", payload);
                // BUG CORRIGÉ : même piège Map.of que
                // notifierMembreBailMisAJour (NullPointerException dès
                // qu'une seule valeur est null) — ici sur LA DIFFUSION
                // DE TOUTE OPÉRATION MULTI (vente, produit, dette...).
                // Si jamais "resultat" ou payload.get("type") se
                // retrouvait null, l'exception remontait jusqu'au catch
                // englobant de cette méthode et renvoyait une ERREUR à
                // l'auteur de l'opération — alors même que
                // traiterOperation() avait déjà réussi et committé en
                // base juste avant. L'utilisateur voyait donc échouer
                // une action qui avait en réalité fonctionné.
                Map<String, Object> messageDiffuse = new HashMap<>();
                messageDiffuse.put("operationType", payload.get("type"));
                messageDiffuse.put("operationUuid", payload.getOrDefault("operationUuid", ""));
                messageDiffuse.put("auteur", auth.getName());
                messageDiffuse.put("data", data);
                messageDiffuse.put("resultat", resultat);
                messageDiffuse.put("horodatageServeur", System.currentTimeMillis());
                groupeWebSocketHandler.diffuser(gUuid, "operation", messageDiffuse);
            }

            return ResponseEntity.ok(ApiResponse.ok("Opération traitée", resultat));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    // GET /api/multi/dashboard/{groupeUuid}
    @GetMapping("/dashboard/{groupeUuid}")
    public ResponseEntity<ApiResponse<Map<String,Object>>> dashboard(
            @PathVariable String groupeUuid) {
        try {
            return ResponseEntity.ok(ApiResponse.ok(
                    multiService.getDashboard(groupeUuid)
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    // GET /api/multi/donnees-completes/{groupeUuid}
    // Snapshot complet du groupe (produits, ventes, clients, dettes,
    // mouvements de stock) — utilisé pour la synchronisation initiale
    // d'un nouveau membre, et pour rattraper les données manquées après
    // une coupure réseau (le WebSocket ne rejoue pas les événements
    // passés pendant une déconnexion).
    @GetMapping("/donnees-completes/{groupeUuid}")
    public ResponseEntity<ApiResponse<Map<String,Object>>> donneesCompletes(
            @PathVariable String groupeUuid) {
        try {
            return ResponseEntity.ok(ApiResponse.ok(
                    multiService.getDonneesCompletes(groupeUuid)
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    // PUT /api/multi/role/membre/{membreUuid}
    // Le propriétaire change le rôle d'un membre (vendeur par défaut,
    // ou toute autre valeur qu'il choisit).
    @PutMapping("/role/membre/{membreUuid}")
    public ResponseEntity<ApiResponse<Map<String,Object>>> modifierRole(
            @PathVariable String membreUuid,
            @RequestBody Map<String,String> body,
            Authentication auth) {
        try {
            Map<String, Object> resultat = multiService.modifierRoleMembre(
                    membreUuid, body.get("role"), auth.getName());
            diffuserSiGroupeConnu(resultat, "membre_role_maj", resultat);
            return ResponseEntity.ok(ApiResponse.ok("Rôle mis à jour", resultat));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    // PUT /api/multi/membre/{membreUuid}/connexion-permanente
    @PutMapping("/membre/{membreUuid}/connexion-permanente")
    public ResponseEntity<ApiResponse<String>> connexionPermanente(
            @PathVariable String membreUuid,
            @RequestBody Map<String, Boolean> body,
            Authentication auth) {
        try {
            multiService.definirConnexionPermanente(
                    membreUuid, Boolean.TRUE.equals(body.get("actif")), auth.getName());
            return ResponseEntity.ok(ApiResponse.ok("ok"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    // PUT /api/multi/membre/{membreUuid}/prolonger-bail
    @PutMapping("/membre/{membreUuid}/prolonger-bail")
    public ResponseEntity<ApiResponse<String>> prolongerBail(
            @PathVariable String membreUuid,
            @RequestBody Map<String, String> body,
            Authentication auth) {
        try {
            multiService.prolongerBail(membreUuid, body.get("nouvelleHeure"), auth.getName());
            return ResponseEntity.ok(ApiResponse.ok("ok"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    // POST /api/multi/membre/{membreUuid}/deconnecter-force
    // Déconnexion forcée PAR LE PROPRIÉTAIRE — ferme réellement la/les
    // session(s) WebSocket ouvertes de ce membre (voir
    // MultiModeService.deconnecterMembreParProprietaire).
    @PostMapping("/membre/{membreUuid}/deconnecter-force")
    public ResponseEntity<ApiResponse<String>> deconnecterForce(
            @PathVariable String membreUuid, Authentication auth) {
        try {
            multiService.deconnecterMembreParProprietaire(membreUuid, auth.getName());
            return ResponseEntity.ok(ApiResponse.ok("ok"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    // GET /api/multi/permissions/membre/{membreUuid}
    @GetMapping("/permissions/membre/{membreUuid}")
    public ResponseEntity<ApiResponse<Map<String,Object>>> getPermissions(
            @PathVariable String membreUuid) {
        try {
            return ResponseEntity.ok(ApiResponse.ok(
                    multiService.getPermissions(membreUuid)
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    // PUT /api/multi/permissions/membre/{membreUuid}
    @PutMapping("/permissions/membre/{membreUuid}")
    public ResponseEntity<ApiResponse<Map<String,Object>>> permissions(
            @PathVariable String membreUuid,
            @RequestBody Map<String,Boolean> body,
            Authentication auth) {
        try {
            Map<String, Object> resultat =
                    multiService.modifierPermissions(membreUuid, body, auth.getName());
            diffuserSiGroupeConnu(resultat, "membre_permissions_maj", resultat);
            return ResponseEntity.ok(ApiResponse.ok(
                    "Permissions mises à jour", resultat));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    /** Diffuse `data` sur le groupe si resultat contient un groupeUuid exploitable. */
    private void diffuserSiGroupeConnu(
            Map<String, Object> resultat, String type, Object data) {
        Object gUuid = resultat != null ? resultat.get("groupeUuid") : null;
        if (gUuid instanceof String s) {
            groupeWebSocketHandler.diffuser(s, type, data);
        }
    }

    // POST /api/multi/activer/{groupeUuid}
    @PostMapping("/activer/{groupeUuid}")
    public ResponseEntity<ApiResponse<Map<String,Object>>> activer(
            @PathVariable String groupeUuid) {
        try {
            return ResponseEntity.ok(ApiResponse.ok(
                    multiService.passerEnModeMulti(groupeUuid)
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    // POST /api/multi/desactiver/{groupeUuid}
    @PostMapping("/desactiver/{groupeUuid}")
    public ResponseEntity<ApiResponse<Map<String,Object>>> desactiver(
            @PathVariable String groupeUuid) {
        try {
            return ResponseEntity.ok(ApiResponse.ok(
                    multiService.passerEnModeSolo(groupeUuid)
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        }
    }
}
