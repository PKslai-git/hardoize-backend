package com.digneequipe.hardoize.controllers;

import com.digneequipe.hardoize.dto.response.ApiResponse;
import com.digneequipe.hardoize.services.AdhesionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/adhesion")
@RequiredArgsConstructor
public class AdhesionController {

    private final AdhesionService adhesionService;

    // POST /api/adhesion/invitation/{groupeUuid} — propriétaire uniquement
    // Génère un jeton d'invitation valable 15 minutes, à usage unique.
    @PostMapping("/invitation/{groupeUuid}")
    public ResponseEntity<ApiResponse<Map<String,Object>>> genererInvitation(
            @PathVariable String groupeUuid, Authentication auth) {
        try {
            return ResponseEntity.ok(ApiResponse.ok(
                    adhesionService.genererInvitation(groupeUuid, auth.getName())
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    // POST /api/adhesion/demander
    // Scan QR / clic sur le lien — crée une demande en attente
    // d'approbation par le propriétaire (ou rejoint directement si déjà
    // approuvé précédemment, cas d'un rescan).
    @PostMapping("/demander")
    public ResponseEntity<ApiResponse<Map<String,Object>>> demander(
            @RequestBody Map<String,String> body, Authentication auth) {
        try {
            return ResponseEntity.ok(ApiResponse.ok(
                    adhesionService.demanderAdhesion(
                            body.get("token"), auth.getName(), body.get("nomAffiche"))
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    // GET /api/adhesion/en-attente/{groupeUuid} — propriétaire uniquement
    @GetMapping("/en-attente/{groupeUuid}")
    public ResponseEntity<ApiResponse<List<Map<String,Object>>>> enAttente(
            @PathVariable String groupeUuid, Authentication auth) {
        try {
            return ResponseEntity.ok(ApiResponse.ok(
                    adhesionService.listerDemandesEnAttente(groupeUuid, auth.getName())
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    // PUT /api/adhesion/{membreUuid}/approuver — propriétaire uniquement
    @PutMapping("/{membreUuid}/approuver")
    public ResponseEntity<ApiResponse<Map<String,Object>>> approuver(
            @PathVariable String membreUuid, Authentication auth) {
        try {
            return ResponseEntity.ok(ApiResponse.ok(
                    adhesionService.traiterDemande(membreUuid, true, auth.getName())
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    // PUT /api/adhesion/{membreUuid}/refuser — propriétaire uniquement
    @PutMapping("/{membreUuid}/refuser")
    public ResponseEntity<ApiResponse<Map<String,Object>>> refuser(
            @PathVariable String membreUuid, Authentication auth) {
        try {
            return ResponseEntity.ok(ApiResponse.ok(
                    adhesionService.traiterDemande(membreUuid, false, auth.getName())
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        }
    }
}
