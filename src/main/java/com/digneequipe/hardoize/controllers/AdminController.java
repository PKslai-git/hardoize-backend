package com.digneequipe.hardoize.controllers;

import com.digneequipe.hardoize.dto.response.ApiResponse;
import com.digneequipe.hardoize.models.Utilisateur;
import com.digneequipe.hardoize.repositories.UtilisateurRepository;
import com.digneequipe.hardoize.services.AccesService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Réservé au développeur de l'application (rôle "administrateur") —
 * permet de prolonger l'accès d'un utilisateur dont la période
 * d'essai d'un mois est arrivée à échéance, le temps des prochains
 * builds de test. Même mécanisme (Utilisateur.dateExpirationAcces)
 * que celui qui servira plus tard à la gestion de l'abonnement payant.
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final UtilisateurRepository utilisateurRepo;
    private final AccesService accesService;

    // PUT /api/admin/utilisateurs/{telephone}/prolonger-acces
    // Body optionnel : { "jours": 30 } (par défaut 30 jours à partir
    // de maintenant). Un compte "administrateur" peut se prolonger
    // lui-même ou n'importe quel autre compte.
    @PutMapping("/utilisateurs/{telephone}/prolonger-acces")
    public ResponseEntity<ApiResponse<Map<String, Object>>> prolongerAcces(
            @PathVariable String telephone,
            @RequestBody(required = false) Map<String, Object> body,
            Authentication auth
    ) {
        try {
            verifierEstAdmin(auth.getName());

            Utilisateur cible = utilisateurRepo.findByTelephone(telephone)
                    .orElseThrow(() -> new RuntimeException(
                            "Aucun utilisateur avec ce numéro"));

            int jours = 30;
            if (body != null && body.get("jours") != null) {
                jours = Integer.parseInt(body.get("jours").toString());
            }

            cible.setDateExpirationAcces(LocalDateTime.now().plusDays(jours));
            utilisateurRepo.save(cible);

            return ResponseEntity.ok(ApiResponse.ok(Map.of(
                    "telephone", cible.getTelephone(),
                    "nouvelleExpiration", cible.getDateExpirationAcces().toString(),
                    "joursRestants", accesService.joursRestants(cible)
            )));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    // GET /api/admin/utilisateurs/{telephone}/acces — consultation
    // rapide de l'état d'essai d'un compte (utile pour vérifier avant
    // de prolonger, ou pour du support).
    @GetMapping("/utilisateurs/{telephone}/acces")
    public ResponseEntity<ApiResponse<Map<String, Object>>> consulterAcces(
            @PathVariable String telephone,
            Authentication auth
    ) {
        try {
            verifierEstAdmin(auth.getName());

            Utilisateur cible = utilisateurRepo.findByTelephone(telephone)
                    .orElseThrow(() -> new RuntimeException(
                            "Aucun utilisateur avec ce numéro"));

            return ResponseEntity.ok(ApiResponse.ok(Map.of(
                    "telephone", cible.getTelephone(),
                    "createdAt", String.valueOf(cible.getCreatedAt()),
                    "dateExpirationAcces",
                    cible.getDateExpirationAcces() != null
                            ? cible.getDateExpirationAcces().toString() : "",
                    "expire", accesService.estExpire(cible),
                    "joursRestants", accesService.joursRestants(cible)
            )));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    private void verifierEstAdmin(String telephoneAuteur) {
        Utilisateur auteur = utilisateurRepo.findByTelephone(telephoneAuteur)
                .orElseThrow(() -> new RuntimeException("Utilisateur introuvable"));
        if (!"administrateur".equalsIgnoreCase(auteur.getRole())) {
            throw new RuntimeException(
                    "Réservé aux comptes administrateur");
        }
    }
}
