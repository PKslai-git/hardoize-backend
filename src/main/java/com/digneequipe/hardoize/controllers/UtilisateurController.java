package com.digneequipe.hardoize.controllers;

import com.digneequipe.hardoize.dto.response.ApiResponse;
import com.digneequipe.hardoize.models.Utilisateur;
import com.digneequipe.hardoize.repositories.UtilisateurRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/utilisateurs")
@RequiredArgsConstructor
public class UtilisateurController {

    private final UtilisateurRepository utilisateurRepo;

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
