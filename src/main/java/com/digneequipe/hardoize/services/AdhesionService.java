package com.digneequipe.hardoize.services;

import com.digneequipe.hardoize.models.*;
import com.digneequipe.hardoize.repositories.*;
import com.digneequipe.hardoize.util.TelephoneUtil;
import com.digneequipe.hardoize.websocket.GroupeWebSocketHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;

/**
 * Remplace l'ancien rejoindreGroupe (MultiModeService), qui rendait un
 * membre actif immédiatement sur simple scan du codeQR permanent du
 * groupe. Désormais en 2 temps :
 *
 *  1. genererInvitation  : le propriétaire génère un jeton à courte
 *     durée de vie (QR + lien encodent ce jeton, pas le codeQR
 *     permanent du groupe).
 *  2. demanderAdhesion   : la personne qui scanne/clique crée une
 *     DEMANDE (statutAdhesion="en_attente") — pas encore membre actif
 *     — le propriétaire est notifié (push) et doit approuver.
 *
 * Ça règle 2 choses demandées : le QR/lien ne peuvent plus être
 * capturés puis réutilisés plus tard (jeton à usage unique + expiré
 * après quelques minutes), et le propriétaire garde la main sur qui
 * entre réellement dans son groupe.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdhesionService {

    private static final int MINUTES_VALIDITE = 15;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String ALPHABET =
            "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // sans 0/O/1/I ambigus

    private final GroupeRepository            groupeRepo;
    private final MembreGroupeRepository      membreRepo;
    private final PermissionMembreRepository  permissionRepo;
    private final UtilisateurRepository       utilisateurRepo;
    private final InvitationGroupeRepository  invitationRepo;
    private final PushNotificationService     pushService;
    private final GroupeWebSocketHandler      groupeWebSocketHandler;

    // ── Générer une invitation (propriétaire uniquement) ───────
    @Transactional
    public Map<String, Object> genererInvitation(String groupeUuid, String telephoneAuteur) {
        Groupe groupe = groupeRepo.findByUuid(groupeUuid)
                .orElseThrow(() -> new RuntimeException("Groupe introuvable"));

        if (!TelephoneUtil.equivalents(
                groupe.getProprietaire().getTelephone(), telephoneAuteur)) {
            throw new RuntimeException("Seul le propriétaire peut générer une invitation");
        }

        String token = genererTokenUnique();
        LocalDateTime expireLe = LocalDateTime.now(ZoneOffset.UTC)
                .plusMinutes(MINUTES_VALIDITE);

        InvitationGroupe invitation = InvitationGroupe.builder()
                .groupe(groupe)
                .token(token)
                .expireLe(expireLe)
                .build();
        invitationRepo.save(invitation);

        Map<String, Object> result = new HashMap<>();
        result.put("token", token);
        result.put("expireLe", expireLe.toString());
        result.put("minutesValidite", MINUTES_VALIDITE);
        return result;
    }

    // ── Demander à rejoindre (scan / clic sur le lien) ─────────
    @Transactional
    public Map<String, Object> demanderAdhesion(
            String token, String telephone, String nomAffiche) {

        InvitationGroupe invitation = invitationRepo.findByToken(token)
                .orElseThrow(() -> new RuntimeException(
                        "Invitation invalide — demandez un nouveau lien/QR au propriétaire"));

        if (Boolean.TRUE.equals(invitation.getRevoquee())) {
            throw new RuntimeException("Cette invitation a été annulée");
        }
        if (Boolean.TRUE.equals(invitation.getUtilisee())) {
            throw new RuntimeException(
                    "Cette invitation a déjà été utilisée — demandez-en une nouvelle");
        }
        if (invitation.getExpireLe().isBefore(LocalDateTime.now(ZoneOffset.UTC))) {
            throw new RuntimeException(
                    "Cette invitation a expiré — demandez un nouveau lien/QR");
        }

        // Usage unique : consommée dès la demande, qu'elle soit ensuite
        // approuvée ou refusée par le propriétaire. Une capture de ce
        // même QR/lien ne fonctionnera plus, même quelques secondes après.
        invitation.setUtilisee(true);
        invitationRepo.save(invitation);

        Groupe groupe = invitation.getGroupe();
        Utilisateur user = utilisateurRepo.findByTelephone(telephone)
                .orElseThrow(() -> new RuntimeException("Utilisateur introuvable"));

        MembreGroupe membre = membreRepo
                .findByGroupeIdAndTelephone(groupe.getId(), telephone)
                .map(m -> {
                    // Redemande après un refus précédent, ou reprise
                    // d'une adhésion déjà approuvée (rescan) : dans ce
                    // dernier cas on n'exige pas une nouvelle
                    // approbation, la personne est déjà membre.
                    if (!"approuve".equals(m.getStatutAdhesion())) {
                        m.setStatutAdhesion("en_attente");
                    }
                    if (nomAffiche != null) m.setNomAffiche(nomAffiche);
                    m.setEstActif(true);
                    return membreRepo.save(m);
                })
                .orElseGet(() -> {
                    MembreGroupe m = MembreGroupe.builder()
                            .groupe(groupe)
                            .utilisateur(user)
                            .nomAffiche(nomAffiche != null ? nomAffiche : user.getNom())
                            .telephone(telephone)
                            .role("vendeur")
                            .bailHeure(groupe.getHeureFermeture())
                            .connexionPermanente(false)
                            .statutAdhesion("en_attente")
                            .build();
                    m = membreRepo.save(m);

                    PermissionMembre perms = PermissionMembre.builder()
                            .membre(m)
                            .peutVendre(true)
                            .peutVoirDettes(false)
                            .peutGererStock(false)
                            .peutVoirStats(false)
                            .peutGererClients(false)
                            .peutVoirHistorique(false)
                            .build();
                    permissionRepo.save(perms);
                    return m;
                });

        boolean dejaApprouve = "approuve".equals(membre.getStatutAdhesion());

        if (!dejaApprouve) {
            String proprioToken = groupe.getProprietaire().getExpoPushToken();
            pushService.envoyer(proprioToken,
                    "Nouvelle demande d'adhésion",
                    (nomAffiche != null ? nomAffiche : user.getNom())
                            + " souhaite rejoindre " + groupe.getNom(),
                    Map.of("type", "demande_adhesion", "groupeUuid", groupe.getUuid()));

            groupeWebSocketHandler.diffuser(groupe.getUuid(), "demande_adhesion", Map.of(
                    "membreUuid", membre.getUuid(),
                    "nomAffiche", membre.getNomAffiche()
            ));
        }

        Map<String, Object> result = new HashMap<>();
        result.put("membreUuid", membre.getUuid());
        result.put("groupeUuid", groupe.getUuid());
        result.put("groupeNom", groupe.getNom());
        result.put("statutAdhesion", membre.getStatutAdhesion());
        result.put("enAttenteApprobation", !dejaApprouve);
        return result;
    }

    // ── Lister les demandes en attente (propriétaire) ──────────
    @Transactional(readOnly = true)
    public List<Map<String, Object>> listerDemandesEnAttente(
            String groupeUuid, String telephoneAuteur) {
        Groupe groupe = groupeRepo.findByUuid(groupeUuid)
                .orElseThrow(() -> new RuntimeException("Groupe introuvable"));

        if (!TelephoneUtil.equivalents(
                groupe.getProprietaire().getTelephone(), telephoneAuteur)) {
            throw new RuntimeException("Seul le propriétaire peut voir les demandes");
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (MembreGroupe m : membreRepo.findByGroupeId(groupe.getId())) {
            if ("en_attente".equals(m.getStatutAdhesion())) {
                Map<String, Object> dto = new HashMap<>();
                dto.put("membreUuid", m.getUuid());
                dto.put("nomAffiche", m.getNomAffiche());
                dto.put("telephone", m.getTelephone());
                dto.put("demandeLe", m.getCreatedAt());
                result.add(dto);
            }
        }
        return result;
    }

    // ── Approuver / refuser une demande (propriétaire) ─────────
    @Transactional
    public Map<String, Object> traiterDemande(
            String membreUuid, boolean approuver, String telephoneAuteur) {

        MembreGroupe membre = membreRepo.findByUuid(membreUuid)
                .orElseThrow(() -> new RuntimeException("Membre introuvable"));
        Groupe groupe = membre.getGroupe();

        if (!TelephoneUtil.equivalents(
                groupe.getProprietaire().getTelephone(), telephoneAuteur)) {
            throw new RuntimeException("Seul le propriétaire peut traiter une demande");
        }

        membre.setStatutAdhesion(approuver ? "approuve" : "refuse");
        if (!approuver) membre.setEstActif(false);
        membreRepo.save(membre);

        if (approuver) {
            long nbMembres = membreRepo.findByGroupeId(groupe.getId()).stream()
                    .filter(m -> "approuve".equals(m.getStatutAdhesion()))
                    .count();
            if (nbMembres > 1) {
                groupe.setMode("multi");
                groupeRepo.save(groupe);
            }
        }

        if (membre.getUtilisateur() != null) {
            pushService.envoyer(membre.getUtilisateur().getExpoPushToken(),
                    approuver ? "Adhésion acceptée" : "Adhésion refusée",
                    approuver
                            ? "Vous avez rejoint " + groupe.getNom()
                            : "Votre demande pour " + groupe.getNom() + " a été refusée",
                    Map.of("type", "reponse_adhesion",
                            "groupeUuid", groupe.getUuid(),
                            "approuve", approuver));
        }

        Map<String, Object> result = new HashMap<>();
        result.put("membreUuid", membre.getUuid());
        result.put("statutAdhesion", membre.getStatutAdhesion());
        result.put("groupeUuid", groupe.getUuid());
        return result;
    }

    private String genererTokenUnique() {
        String token;
        do {
            StringBuilder sb = new StringBuilder(10);
            for (int i = 0; i < 10; i++) {
                sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
            }
            token = sb.toString();
        } while (invitationRepo.findByToken(token).isPresent());
        return token;
    }
}
