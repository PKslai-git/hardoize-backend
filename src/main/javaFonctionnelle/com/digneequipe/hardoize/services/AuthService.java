package com.digneequipe.hardoize.services;

import com.digneequipe.hardoize.dto.request.*;
import com.digneequipe.hardoize.dto.response.*;
import com.digneequipe.hardoize.models.Utilisateur;
import com.digneequipe.hardoize.repositories.UtilisateurRepository;
import com.digneequipe.hardoize.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UtilisateurRepository utilisateurRepository;
    private final PasswordEncoder       passwordEncoder;
    private final JwtUtil               jwtUtil;
    private final AuthenticationManager authenticationManager;

    // ── Inscription ───────────────────────────────────────────
    @Transactional
    public AuthResponse inscrire(RegisterRequest request) {

        // Vérifier si le téléphone existe déjà
        if (utilisateurRepository.existsByTelephone(request.getTelephone())) {
            throw new RuntimeException("Ce numéro de téléphone est déjà utilisé");
        }

        // Vérifier si l'email existe déjà (si fourni)
        if (request.getEmail() != null && !request.getEmail().isBlank() &&
                utilisateurRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Cet email est déjà utilisé");
        }

        // Créer l'utilisateur avec mot de passe hashé BCrypt
        Utilisateur utilisateur = Utilisateur.builder()
                .nom(request.getNom())
                .telephone(request.getTelephone())
                .email(request.getEmail())
                .motDePasse(passwordEncoder.encode(request.getMotDePasse()))
                .role(request.getRole() != null ? request.getRole() : "vendeur")
                .build();

        utilisateur = utilisateurRepository.save(utilisateur);

        // Générer les tokens JWT
        String token        = jwtUtil.genererToken(utilisateur.getTelephone());
        String refreshToken = jwtUtil.genererRefreshToken(utilisateur.getTelephone());

        return AuthResponse.builder()
                .token(token)
                .refreshToken(refreshToken)
                .utilisateur(mapToResponse(utilisateur))
                .build();
    }

    // ── Connexion ─────────────────────────────────────────────
    public AuthResponse connecter(AuthRequest request) {

        // Vérifier les identifiants via Spring Security
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getTelephone(),
                            request.getMotDePasse()
                    )
            );
        } catch (BadCredentialsException e) {
            throw new RuntimeException("Numéro ou mot de passe incorrect");
        }

        // Charger l'utilisateur
        Utilisateur utilisateur = utilisateurRepository
                .findByTelephone(request.getTelephone())
                .orElseThrow(() -> new RuntimeException("Utilisateur introuvable"));

        // Générer les tokens
        String token        = jwtUtil.genererToken(utilisateur.getTelephone());
        String refreshToken = jwtUtil.genererRefreshToken(utilisateur.getTelephone());

        return AuthResponse.builder()
                .token(token)
                .refreshToken(refreshToken)
                .utilisateur(mapToResponse(utilisateur))
                .build();
    }

    // ── Refresh token ─────────────────────────────────────────
    public AuthResponse refreshToken(String refreshToken) {
        if (!jwtUtil.validerToken(refreshToken)) {
            throw new RuntimeException("Refresh token invalide ou expiré");
        }

        String telephone = jwtUtil.extraireTelephone(refreshToken);
        Utilisateur utilisateur = utilisateurRepository
                .findByTelephone(telephone)
                .orElseThrow(() -> new RuntimeException("Utilisateur introuvable"));

        String nouveauToken = jwtUtil.genererToken(telephone);

        return AuthResponse.builder()
                .token(nouveauToken)
                .refreshToken(refreshToken)
                .utilisateur(mapToResponse(utilisateur))
                .build();
    }

    // ── Mapper Utilisateur → UtilisateurResponse ──────────────
    public UtilisateurResponse mapToResponse(Utilisateur u) {
        return UtilisateurResponse.builder()
                .id(u.getId())
                .nom(u.getNom())
                .telephone(u.getTelephone())
                .email(u.getEmail())
                .role(u.getRole())
                .photoUri(u.getPhotoUri())
                .estActif(u.getEstActif())
                .createdAt(u.getCreatedAt())
                .build();
    }
}