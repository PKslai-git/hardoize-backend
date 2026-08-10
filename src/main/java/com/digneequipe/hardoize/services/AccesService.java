package com.digneequipe.hardoize.services;

import com.digneequipe.hardoize.models.Utilisateur;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * Détermine si l'accès d'un utilisateur est encore valide.
 *
 * Règle par défaut : 30 jours à partir de la création du compte
 * (Utilisateur.createdAt) — le "mois d'essai". Ce même mécanisme sert
 * de base pour la future gestion d'abonnement payant : le jour venu,
 * il suffira d'alimenter dateExpirationAcces à chaque paiement validé
 * au lieu de ne l'utiliser que pour les prolongations manuelles.
 *
 * dateExpirationAcces, quand elle est renseignée, prévaut TOUJOURS sur
 * le calcul par défaut (que ce soit pour prolonger ou, plus tard, pour
 * gérer un abonnement expiré plus tôt que le mois d'essai n'aurait
 * expiré seul).
 */
@Service
public class AccesService {

    public static final int DUREE_ESSAI_JOURS = 30;

    public boolean estExpire(Utilisateur u) {
        if (u == null) return true;
        LocalDateTime maintenant = LocalDateTime.now();

        if (u.getDateExpirationAcces() != null) {
            return maintenant.isAfter(u.getDateExpirationAcces());
        }

        LocalDateTime debut = u.getCreatedAt() != null
                ? u.getCreatedAt() : maintenant;
        return maintenant.isAfter(debut.plusDays(DUREE_ESSAI_JOURS));
    }

    public long joursRestants(Utilisateur u) {
        if (u == null) return 0;
        LocalDateTime maintenant = LocalDateTime.now();
        LocalDateTime echeance = u.getDateExpirationAcces() != null
                ? u.getDateExpirationAcces()
                : (u.getCreatedAt() != null ? u.getCreatedAt() : maintenant)
                        .plusDays(DUREE_ESSAI_JOURS);
        long jours = java.time.Duration.between(maintenant, echeance).toDays();
        return Math.max(0, jours);
    }
}
