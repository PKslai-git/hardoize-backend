package com.digneequipe.hardoize.controllers;

import com.digneequipe.hardoize.dto.request.AuthRequest;
import com.digneequipe.hardoize.dto.response.ApiResponse;
import com.digneequipe.hardoize.models.Utilisateur;
import com.digneequipe.hardoize.repositories.UtilisateurRepository;
import com.digneequipe.hardoize.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UtilisateurRepository utilisateurRepo;
    private final JwtService            jwtService;
    private final PasswordEncoder       passwordEncoder;

    // POST /api/auth/register
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<Map<String, Object>>> register(
            @RequestBody AuthRequest req) {
        try {
            if (utilisateurRepo.existsByTelephone(req.getTelephone())) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("Téléphone déjà utilisé"));
            }

            // Correction : le rôle choisi (vendeur/propriétaire) envoyé par
            // RegisterScreen.js/authService.js n'était jamais lu ni
            // enregistré ici — l'utilisateur était toujours créé sans
            // rôle explicite (donc valeur par défaut de l'entité, pas
            // le choix fait à l'écran d'inscription).
            String role = (req.getRole() != null && !req.getRole().isBlank())
                    ? req.getRole()
                    : "vendeur";

            Utilisateur user = Utilisateur.builder()
                    .nom(req.getNom())
                    .telephone(req.getTelephone())
                    .motDePasse(passwordEncoder.encode(req.getMotDePasse()))
                    .role(role)
                    .build();
            user = utilisateurRepo.save(user);

            String token = jwtService.genererToken(req.getTelephone());

            Map<String, Object> result = buildUserDto(user, token);
            return ResponseEntity.ok(ApiResponse.ok("Inscription réussie", result));

        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    // POST /api/auth/login
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<Map<String, Object>>> login(
            @RequestBody AuthRequest req) {
        try {
            Utilisateur user = utilisateurRepo
                    .findByTelephone(req.getTelephone())
                    .orElseThrow(() ->
                            new RuntimeException("Identifiants incorrects"));

            if (!passwordEncoder.matches(
                    req.getMotDePasse(), user.getMotDePasse())) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("Identifiants incorrects"));
            }

            String token = jwtService.genererToken(req.getTelephone());
            Map<String, Object> result = buildUserDto(user, token);
            return ResponseEntity.ok(ApiResponse.ok("Connexion réussie", result));

        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    // Correction : "role" (et email/photoUri, utiles côté app) manquaient
    // entièrement de cette réponse. Résultat : même quand le rôle était
    // correctement enregistré en base, le frontend ne le recevait jamais
    // et retombait sur son fallback "vendeur" par défaut — à CHAQUE
    // login, pas seulement à l'inscription.
    private Map<String, Object> buildUserDto(Utilisateur u, String token) {
        Map<String, Object> dto = new HashMap<>();
        dto.put("id",        u.getId());
        dto.put("uuid",      u.getUuid());
        dto.put("nom",       u.getNom());
        dto.put("telephone", u.getTelephone());
        dto.put("email",     u.getEmail());
        dto.put("role",      u.getRole());
        dto.put("photoUri",  u.getPhotoUri());
        dto.put("token",     token);
        return dto;
    }
}