package com.digneequipe.hardoize.controllers;

import com.digneequipe.hardoize.dto.response.ApiResponse;
import com.digneequipe.hardoize.services.GroupeService;
import com.digneequipe.hardoize.services.PermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/groupes")
@RequiredArgsConstructor
public class GroupeController {

    private final GroupeService     groupeService;
    private final PermissionService permissionService;

    // POST /api/groupes
    @PostMapping
    public ResponseEntity<ApiResponse<Map<String,Object>>> creer(
            @RequestBody Map<String,Object> body,
            Authentication auth) {
        try {
            return ResponseEntity.ok(ApiResponse.ok(
                    "Groupe créé",
                    groupeService.creer(body, auth.getName())
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    // PUT /api/groupes/{uuid}
    @PutMapping("/{uuid}")
    public ResponseEntity<ApiResponse<Map<String,Object>>> modifier(
            @PathVariable String uuid,
            @RequestBody Map<String,Object> body,
            Authentication auth) {
        try {
            return ResponseEntity.ok(ApiResponse.ok(
                    "Groupe modifié",
                    groupeService.modifierParUuid(uuid, body, auth.getName())
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    // GET /api/groupes
    @GetMapping
    public ResponseEntity<ApiResponse<List<Map<String,Object>>>> getAll(
            Authentication auth) {
        return ResponseEntity.ok(ApiResponse.ok(
                groupeService.getByProprietaire(auth.getName())
        ));
    }

    // GET /api/groupes/{uuid}/membres
    @GetMapping("/{uuid}/membres")
    public ResponseEntity<ApiResponse<List<Map<String,Object>>>> getMembres(
            @PathVariable String uuid,
            Authentication auth) {
        try {
            return ResponseEntity.ok(ApiResponse.ok(
                    groupeService.getMembresParUuid(uuid, auth.getName())
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    // PUT /api/groupes/membres/{membreUuid}/permissions
    @PutMapping("/membres/{membreUuid}/permissions")
    public ResponseEntity<ApiResponse<Map<String,Object>>> modifierPermissions(
            @PathVariable String membreUuid,
            @RequestBody Map<String,Boolean> permissions,
            Authentication auth) {
        try {
            return ResponseEntity.ok(ApiResponse.ok(
                    "Permissions mises à jour",
                    permissionService.modifierParUuid(membreUuid, permissions)
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    // GET /api/groupes/membres/{membreUuid}/permissions
    @GetMapping("/membres/{membreUuid}/permissions")
    public ResponseEntity<ApiResponse<Map<String,Object>>> getPermissions(
            @PathVariable String membreUuid) {
        try {
            return ResponseEntity.ok(ApiResponse.ok(
                    permissionService.getByMembreUuid(membreUuid)
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    // DELETE /api/groupes/membres/{membreUuid}
    @DeleteMapping("/membres/{membreUuid}")
    public ResponseEntity<ApiResponse<Void>> retirerMembre(
            @PathVariable String membreUuid,
            Authentication auth) {
        try {
            groupeService.retirerMembreParUuid(membreUuid, auth.getName());
            return ResponseEntity.ok(ApiResponse.ok(null));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        }
    }
}