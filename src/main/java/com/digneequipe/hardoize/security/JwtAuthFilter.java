package com.digneequipe.hardoize.security;

import com.digneequipe.hardoize.models.Utilisateur;
import com.digneequipe.hardoize.repositories.UtilisateurRepository;
import com.digneequipe.hardoize.services.AccesService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UtilisateurRepository utilisateurRepo;
    private final AccesService accesService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Chemins jamais bloqués par le contrôle d'essai/abonnement, même
    // pour un compte expiré : l'authentification elle-même (sinon
    // personne d'expiré ne pourrait même se reconnecter pour voir le
    // message), le endpoint de prolongation admin (sinon impossible
    // de se prolonger soi-même une fois expiré), et les sondes
    // techniques (health/version).
    private static final String[] CHEMINS_EXEMPTS = {
            "/api/auth/", "/api/health", "/api/version",
            "/api/admin/",
    };

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain)
            throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            if (jwtService.validerToken(token)) {
                String telephone = jwtService.extraireTelephone(token);
                UsernamePasswordAuthenticationToken auth =
                        new UsernamePasswordAuthenticationToken(
                                telephone, null, Collections.emptyList()
                        );
                SecurityContextHolder.getContext().setAuthentication(auth);

                // Blocage automatique après le mois d'essai (ou la
                // date de prolongation/abonnement) — voir
                // AccesService. Le client a besoin d'une réponse
                // reconnaissable (code + champ dédié) pour afficher un
                // écran clair plutôt qu'une erreur générique.
                if (estCheminSoumis(request.getRequestURI())) {
                    Optional<Utilisateur> u = utilisateurRepo.findByTelephone(telephone);
                    if (u.isPresent() && accesService.estExpire(u.get())) {
                        repondreEssaiExpire(response);
                        return;
                    }
                }
            }
        }

        chain.doFilter(request, response);
    }

    private boolean estCheminSoumis(String uri) {
        for (String prefixe : CHEMINS_EXEMPTS) {
            if (uri.startsWith(prefixe) || uri.contains(prefixe)) return false;
        }
        return true;
    }

    private void repondreEssaiExpire(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json");
        response.getWriter().write(objectMapper.writeValueAsString(Map.of(
                "success", false,
                "error", "ESSAI_EXPIRE",
                "message",
                "Votre période d'essai d'un mois est terminée. " +
                "Contactez le développeur pour la prolonger, ou " +
                "attendez la mise en place prochaine de l'abonnement."
        )));
    }
}