package com.digneequipe.hardoize.controllers;

import com.digneequipe.hardoize.dto.response.ApiResponse;
import com.digneequipe.hardoize.models.Utilisateur;
import com.digneequipe.hardoize.repositories.UtilisateurRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/utilisateurs")
@RequiredArgsConstructor
public class UtilisateurController {

    private final UtilisateurRepository utilisateurRepo;

    // PUT /api/utilisateurs/mon-profil
    // Modifier ses propres informations (nom, email, photo).
    // Le téléphone n'est JAMAIS modifiable ici — il sert d'identité
    // de connexion et à retrouver l'utilisateur (auth.getName()),
    // le changer casserait l'authentification et toutes les
    // références (membres_groupe.telephone, etc.).
    @PutMapping("/mon-profil")
    public ResponseEntity<ApiResponse<Map<String, Object>>> modifierMonProfil(
            @RequestBody Map<String, String> body,
            Authentication auth) {
        try {
            Utilisateur u = utilisateurRepo.findByTelephone(auth.getName())
                    .orElseThrow(() -> new RuntimeException("Utilisateur introuvable"));

            if (body.containsKey("nom") && body.get("nom") != null
                    && !body.get("nom").isBlank()) {
                u.setNom(body.get("nom").trim());
            }
            if (body.containsKey("email")) {
                u.setEmail(body.get("email"));
            }
            if (body.containsKey("photoUri")) {
                u.setPhotoUri(body.get("photoUri"));
            }

            u = utilisateurRepo.save(u);

            Map<String, Object> dto = new HashMap<>();
            dto.put("id",        u.getId());
            dto.put("uuid",      u.getUuid());
            dto.put("nom",       u.getNom());
            dto.put("telephone", u.getTelephone());
            dto.put("email",     u.getEmail());
            dto.put("role",      u.getRole());
            dto.put("photoUri",  u.getPhotoUri());

            return ResponseEntity.ok(ApiResponse.ok("Profil mis à jour", dto));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @PutMapping("/{id}/role")
    public ResponseEntity<ApiResponse<Map<String,Object>>> updateRole(
            @PathVariable Long id,
            @RequestBody Map<String,String> body) {
        try {
            Utilisateur u = utilisateurRepo.findById(id)
                    .orElseThrow(() ->
                            new RuntimeException("Utilisateur introuvable"));
            u.setRole(body.getOrDefault("role", "vendeur"));
            utilisateurRepo.save(u);
            return ResponseEntity.ok(ApiResponse.ok(
                    "Rôle mis à jour",
                    Map.of("id", u.getId(), "role", u.getRole())
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        }
    }
}
