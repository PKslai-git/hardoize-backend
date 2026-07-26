package com.digneequipe.hardoize.controllers;

import com.digneequipe.hardoize.dto.response.ApiResponse;
import com.digneequipe.hardoize.services.MultiModeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/multi")
@RequiredArgsConstructor
public class MultiModeController {

    private final MultiModeService multiService;

    // POST /api/multi/rejoindre
    @PostMapping("/rejoindre")
    public ResponseEntity<ApiResponse<Map<String,Object>>> rejoindre(
            @RequestBody Map<String,String> body,
            Authentication auth) {
        try {
            return ResponseEntity.ok(ApiResponse.ok(
                    "Groupe rejoint",
                    multiService.rejoindreGroupe(
                            body.get("codeQR"),
                            auth.getName(),
                            body.get("nomAffiche")
                    )
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    // GET /api/multi/sync/{groupeUuid}
    @GetMapping("/sync/{groupeUuid}")
    public ResponseEntity<ApiResponse<Map<String,Object>>> sync(
            @PathVariable String groupeUuid,
            @RequestParam(required = false) String depuis) {
        try {
            return ResponseEntity.ok(ApiResponse.ok(
                    multiService.getSyncData(groupeUuid, depuis)
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    // POST /api/multi/operation
    @PostMapping("/operation")
    public ResponseEntity<ApiResponse<Map<String,Object>>> operation(
            @RequestBody Map<String,Object> payload,
            Authentication auth) {
        try {
            return ResponseEntity.ok(ApiResponse.ok(
                    "Opération traitée",
                    multiService.traiterOperation(payload, auth.getName())
            ));
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
    // d'un nouveau membre ou d'un nouveau téléphone qui rejoint le groupe.
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
            return ResponseEntity.ok(ApiResponse.ok(
                    "Rôle mis à jour",
                    multiService.modifierRoleMembre(
                            membreUuid, body.get("role"), auth.getName())
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    // POST /api/multi/deconnecter/{membreUuid}
    @PostMapping("/deconnecter/{membreUuid}")
    public ResponseEntity<ApiResponse<Void>> deconnecter(
            @PathVariable String membreUuid) {
        multiService.deconnecterMembre(membreUuid);
        return ResponseEntity.ok(ApiResponse.ok(null));
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
            @RequestBody Map<String,Boolean> body) {
        try {
            return ResponseEntity.ok(ApiResponse.ok(
                    "Permissions mises à jour",
                    multiService.modifierPermissions(membreUuid, body)
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
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