package com.digneequipe.hardoize.services;

import com.digneequipe.hardoize.models.*;
import com.digneequipe.hardoize.repositories.*;
import com.digneequipe.hardoize.websocket.GroupeWebSocketHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.Duration;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class MultiModeService {

    private final GroupeRepository           groupeRepo;
    private final MembreGroupeRepository     membreRepo;
    private final PermissionMembreRepository permissionRepo;
    private final UtilisateurRepository      utilisateurRepo;
    private final VenteService               venteService;
    private final ProduitService             produitService;
    private final ClientService              clientService;
    private final DetteService               detteService;
    private final MouvementStockService      mouvementService;
    private final FournisseurService         fournisseurService;
    private final DetteFournisseurService    detteFournisseurService;
    private final HistoriqueService          historiqueService;
    private final GroupeWebSocketHandler     groupeWebSocketHandler;

    // ── Exécuter une opération en mode multi ─────────────────
    @Transactional
    public Map<String, Object> traiterOperation(
            Map<String, Object> payload, String telephone) {

        String type     = s(payload, "type");
        String gUuidStr = s(payload, "groupeUuid");
        if (gUuidStr == null)
            throw new RuntimeException("groupeUuid manquant");

        Groupe groupe = groupeRepo.findByUuid(gUuidStr)
                .orElseThrow(() -> new RuntimeException("Groupe introuvable"));

        // Vérifier permission
        MembreGroupe membre = membreRepo
                .findByGroupeIdAndTelephone(groupe.getId(), telephone)
                .orElseThrow(() ->
                        new RuntimeException("Membre introuvable dans ce groupe"));

        if (!"approuve".equals(membre.getStatutAdhesion())) {
            throw new RuntimeException(
                    "Adhésion à ce groupe pas encore approuvée par le propriétaire");
        }

        verifierPermission(membre.getId(), type);

        return switch (type != null ? type : "") {
            case "vente" -> {
                @SuppressWarnings("unchecked")
                Map<String, Object> data =
                        (Map<String, Object>) payload.get("data");
                if (data == null) data = payload;
                yield venteService.enregistrerMulti(data, telephone);
            }
            case "mouvement_stock" -> {
                @SuppressWarnings("unchecked")
                Map<String, Object> data =
                        (Map<String, Object>) payload.get("data");
                if (data == null) data = payload;
                yield mouvementService.enregistrerMulti(data, telephone);
            }
            case "fournisseur" -> {
                @SuppressWarnings("unchecked")
                Map<String, Object> data =
                        (Map<String, Object>) payload.get("data");
                if (data == null) data = payload;
                yield fournisseurService.creerOuMettreAJour(data);
            }
            case "client" -> {
                @SuppressWarnings("unchecked")
                Map<String, Object> data =
                        (Map<String, Object>) payload.get("data");
                if (data == null) data = payload;
                yield clientService.creerOuMettreAJour(data, telephone);
            }
            case "produit" -> {
                @SuppressWarnings("unchecked")
                Map<String, Object> data =
                        (Map<String, Object>) payload.get("data");
                if (data == null) data = payload;
                yield produitService.creerOuMettreAJour(data, telephone);
            }
            case "dette" -> {
                @SuppressWarnings("unchecked")
                Map<String, Object> data =
                        (Map<String, Object>) payload.get("data");
                if (data == null) data = payload;
                yield detteService.creerOuMaj(data);
            }
            case "dette_fournisseur" -> {
                @SuppressWarnings("unchecked")
                Map<String, Object> data =
                        (Map<String, Object>) payload.get("data");
                if (data == null) data = payload;
                yield detteFournisseurService.creerOuMaj(data);
            }
            case "dette_remboursement" -> {
                @SuppressWarnings("unchecked")
                Map<String, Object> data =
                        (Map<String, Object>) payload.get("data");
                if (data == null) data = payload;
                String detteUuid = s(data, "uuid");
                if (detteUuid == null)
                    throw new RuntimeException("uuid de la dette manquant");
                double montant = d(data, "montant") != null ? d(data, "montant") : 0.0;
                yield detteService.rembourser(detteUuid, montant, telephone);
            }
            case "dette_fournisseur_remboursement" -> {
                @SuppressWarnings("unchecked")
                Map<String, Object> data =
                        (Map<String, Object>) payload.get("data");
                if (data == null) data = payload;
                String detteUuid = s(data, "uuid");
                if (detteUuid == null)
                    throw new RuntimeException("uuid de la dette fournisseur manquant");
                double montant = d(data, "montant") != null ? d(data, "montant") : 0.0;
                yield detteFournisseurService.rembourser(detteUuid, montant, telephone);
            }
            default -> throw new RuntimeException(
                    "Type d'opération non supporté: " + type);
        };
    }

    // ── Dashboard propriétaire ────────────────────────────────
    @Transactional(readOnly = true)
    public Map<String, Object> getDashboard(String groupeUuid) {
        Groupe groupe = groupeRepo.findByUuid(groupeUuid)
                .orElseThrow(() -> new RuntimeException("Groupe introuvable"));

        List<MembreGroupe> membres = membreRepo.findByGroupeId(groupe.getId());

        List<Map<String, Object>> membresDto = new ArrayList<>();
        for (MembreGroupe m : membres) {
            Map<String, Object> dto = new HashMap<>();
            // Plus d'"id" numérique exposé : uuid uniquement, seul
            // identifiant fiable entre le serveur et chaque appareil.
            dto.put("uuid",        m.getUuid());
            dto.put("nomAffiche",  m.getNomAffiche());
            dto.put("telephone",   m.getTelephone());
            dto.put("role",        m.getRole());
            dto.put("estConnecte", estReellementConnecte(m));
            dto.put("bailHeure",   m.getBailHeure());
            permissionRepo.findByMembreId(m.getId())
                    .ifPresent(p -> dto.put("permissions",
                            buildPermissionsDto(p)));
            membresDto.add(dto);
        }

        Map<String, Object> dashboard = new HashMap<>();
        dashboard.put("membres",    membresDto);
        dashboard.put("mode",       groupe.getMode());
        dashboard.put("groupeUuid", groupe.getUuid());
        dashboard.put("timestamp",  LocalDateTime.now(ZoneOffset.UTC).toString());
        return dashboard;
    }

    // ── Données complètes du groupe (nouveau membre / nouvel appareil) ──
    // Utilisé pour la synchronisation initiale d'un nouveau membre ou
    // d'un nouveau téléphone qui rejoint un groupe : renvoie un instantané
    // complet des données du groupe, identifié uniquement par uuid (jamais
    // par id numérique, propre à chaque base locale).
    @Transactional(readOnly = true)
    public Map<String, Object> getDonneesCompletes(String groupeUuid) {
        Groupe groupe = groupeRepo.findByUuid(groupeUuid)
                .orElseThrow(() -> new RuntimeException("Groupe introuvable"));

        Long id = groupe.getId();

        Map<String, Object> data = new HashMap<>();
        data.put("groupeUuid",      groupe.getUuid());
        data.put("mode",            groupe.getMode());
        data.put("produits",        produitService.getByGroupe(id, true));
        data.put("ventes",          venteService.getByGroupe(id, true));
        data.put("clients",         clientService.getByGroupe(id));
        data.put("dettes",          detteService.getByGroupe(id));
        data.put("mouvementsStock", mouvementService.getByGroupe(id));
        data.put("fournisseurs",    fournisseurService.getByGroupe(id));
        data.put("dettesFournisseurs", detteFournisseurService.getByGroupe(id));
        data.put("historiqueVentes",   historiqueService.getHistoriqueVentes(id));
        // BUG CORRIGÉ : historiquePaiements était absent de ce snapshot —
        // un appareil qui se reconnectait après une coupure ne rattrapait
        // donc jamais les remboursements (clients/fournisseurs) manqués
        // pendant qu'il était déconnecté, même si dettes/dettesFournisseurs
        // (les soldes) étaient, eux, bien rattrapés.
        data.put("historiquePaiements", historiqueService.getHistoriquePaiements(id));
        data.put("timestamp",       LocalDateTime.now(ZoneOffset.UTC).toString());
        return data;
    }

    // ── Modifier le rôle d'un membre (par le propriétaire) ────
    @Transactional
    public Map<String, Object> modifierRoleMembre(
            String membreUuid, String nouveauRole, String telephoneAuteur) {

        if (nouveauRole == null || nouveauRole.isBlank())
            throw new RuntimeException("Rôle invalide");

        MembreGroupe membre = membreRepo.findByUuid(membreUuid)
                .orElseThrow(() -> new RuntimeException("Membre introuvable"));

        Groupe groupe = membre.getGroupe();
        if (!telephonesEquivalents(groupe.getProprietaire().getTelephone(), telephoneAuteur)) {
            throw new RuntimeException(
                    "Seul le propriétaire peut modifier le rôle d'un membre");
        }
        if ("proprietaire".equals(membre.getRole())) {
            throw new RuntimeException(
                    "Impossible de modifier le rôle du propriétaire");
        }

        membre.setRole(nouveauRole.trim().toLowerCase());
        membre = membreRepo.save(membre);

        Map<String, Object> dto = new HashMap<>();
        dto.put("uuid", membre.getUuid());
        dto.put("role", membre.getRole());
        dto.put("groupeUuid", groupe.getUuid());
        return dto;
    }

    // ── Connexion permanente (activer/désactiver, propriétaire) ──
    // Un membre en connexion permanente n'est jamais déconnecté
    // automatiquement à l'échéance du bail (voir planifierBail côté
    // client, qui vérifie ce champ avant de programmer la déconnexion).
    @Transactional
    public void definirConnexionPermanente(
            String membreUuid, boolean actif, String telephoneAuteur) {
        MembreGroupe membre = membreRepo.findByUuid(membreUuid)
                .orElseThrow(() -> new RuntimeException("Membre introuvable"));
        verifierEstProprietaire(membre, telephoneAuteur);
        membre.setConnexionPermanente(actif);
        membreRepo.save(membre);
        // BUG CORRIGÉ : sans cette notification, le membre concerné ne
        // découvrait jamais le changement avant sa prochaine
        // reconnexion complète — son appareil gardait programmée
        // l'ancienne minuterie de bail (ou continuait, à l'inverse, de
        // se croire protégé après une désactivation) jusqu'à l'échéance.
        notifierMembreBailMisAJour(membre);
    }

    // ── Prolonger (ou modifier) l'heure de bail d'un membre ────
    @Transactional
    public void prolongerBail(
            String membreUuid, String nouvelleHeure, String telephoneAuteur) {
        if (nouvelleHeure == null || !nouvelleHeure.matches("\\d{2}:\\d{2}"))
            throw new RuntimeException("Heure invalide (format attendu HH:mm)");
        MembreGroupe membre = membreRepo.findByUuid(membreUuid)
                .orElseThrow(() -> new RuntimeException("Membre introuvable"));
        verifierEstProprietaire(membre, telephoneAuteur);
        membre.setBailHeure(nouvelleHeure);
        membreRepo.save(membre);
        // BUG CORRIGÉ : le prolongement n'était jamais appliqué en
        // pratique — l'appareil du membre gardait sa minuterie locale
        // programmée sur l'ANCIENNE heure (calculée une seule fois à
        // la connexion) et se déconnectait quand même à l'heure
        // d'origine. On pousse maintenant la nouvelle heure en direct
        // pour qu'il reprogramme sa minuterie immédiatement.
        notifierMembreBailMisAJour(membre);
    }

    private void notifierMembreBailMisAJour(MembreGroupe membre) {
        if (membre.getUtilisateur() == null || membre.getGroupe() == null) return;
        groupeWebSocketHandler.envoyerAUtilisateur(
                membre.getGroupe().getUuid(), membre.getUtilisateur().getId(),
                "membre_bail_maj",
                Map.of(
                        "membreUuid", membre.getUuid(),
                        "bailHeure", membre.getBailHeure(),
                        "connexionPermanente", Boolean.TRUE.equals(membre.getConnexionPermanente())
                ));
    }

    // ── Déconnexion forcée par le propriétaire ─────────────────
    // BUG CORRIGÉ : ne fermait que la session WebSocket en cours —
    // sans toucher au statut d'adhésion, l'appareil du membre se
    // reconnectait tout seul (reconnexion automatique avec backoff,
    // voir websocketManager.js côté frontend) quelques secondes plus
    // tard, comme si de rien n'était. On repasse maintenant son
    // adhésion en "en_attente" : toute reconnexion — automatique ou
    // en retapant sur le groupe — est refusée dès la poignée de main
    // WebSocket (voir JwtHandshakeInterceptor) tant que le propriétaire
    // ne l'a pas de nouveau approuvée. La connexion permanente est
    // également désactivée : une déconnexion forcée doit "tenir",
    // pas être immédiatement contournée par ce réglage.
    @Transactional
    public void deconnecterMembreParProprietaire(
            String membreUuid, String telephoneAuteur) {
        MembreGroupe membre = membreRepo.findByUuid(membreUuid)
                .orElseThrow(() -> new RuntimeException("Membre introuvable"));
        verifierEstProprietaire(membre, telephoneAuteur);

        if (membre.getUtilisateur() != null && membre.getGroupe() != null) {
            // Notifier AVANT de fermer la session (sinon plus personne
            // pour recevoir le message) — le frontend affiche un
            // message dédié plutôt que de laisser deviner l'utilisateur
            // pourquoi il vient d'être coupé.
            groupeWebSocketHandler.envoyerAUtilisateur(
                    membre.getGroupe().getUuid(), membre.getUtilisateur().getId(),
                    "deconnecte_force", Map.of("membreUuid", membre.getUuid()));
        }

        membre.setStatutAdhesion("en_attente");
        membre.setConnexionPermanente(false);
        membreRepo.save(membre);

        if (membre.getUtilisateur() != null) {
            groupeWebSocketHandler.fermerSessionsDeUtilisateur(
                    membre.getGroupe().getUuid(), membre.getUtilisateur().getId());
        }
    }

    private void verifierEstProprietaire(MembreGroupe membre, String telephoneAuteur) {
        if (!telephonesEquivalents(
                membre.getGroupe().getProprietaire().getTelephone(), telephoneAuteur)) {
            throw new RuntimeException(
                    "Seul le propriétaire peut effectuer cette action");
        }
    }

    // Compare deux numéros de téléphone en ignorant les espaces, tirets
    // et un éventuel préfixe international (+237, 00237...) — un
    // numéro peut être stocké/renvoyé sous des formats légèrement
    // différents selon le point d'entrée (inscription, JWT, saisie
    // manuelle en base), et une simple comparaison stricte pouvait à
    // tort refuser l'accès au propriétaire lui-même.
    private boolean telephonesEquivalents(String a, String b) {
        return com.digneequipe.hardoize.util.TelephoneUtil.equivalents(a, b);
    }

    // ── Lire les permissions d'un membre ──────────────────────
    @Transactional
    public Map<String, Object> getPermissions(String membreUuid) {
        MembreGroupe membre = membreRepo.findByUuid(membreUuid)
                .orElseThrow(() -> new RuntimeException("Membre introuvable"));

        // Auto-réparation : un membre créé manuellement en base (ou
        // par un ancien flux avant que rejoindreGroupe() ne crée
        // systématiquement une ligne de permissions) peut ne pas en
        // avoir. Avant, ce cas levait "Permissions introuvables" et
        // bloquait tout — on crée maintenant des permissions par
        // défaut à la volée plutôt que d'échouer.
        // Défaut aligné sur assurerPermissionsDefaut (SyncService) et
        // l'auto-réparation de verifierPermission ci-dessous : un
        // propriétaire sans ligne de permissions obtient l'accès
        // complet, pas seulement peutVendre.
        boolean estProprietaireDefaut = "proprietaire".equals(membre.getRole());
        PermissionMembre p = permissionRepo
                .findByMembreId(membre.getId())
                .orElseGet(() -> {
                    PermissionMembre def = PermissionMembre.builder()
                            .membre(membre)
                            .peutVendre(true)
                            .peutVoirDettes(estProprietaireDefaut)
                            .peutGererStock(estProprietaireDefaut)
                            .peutVoirStats(estProprietaireDefaut)
                            .peutGererClients(estProprietaireDefaut)
                            .peutVoirHistorique(estProprietaireDefaut)
                            .build();
                    return permissionRepo.save(def);
                });
        Map<String, Object> dto = buildPermissionsDto(p);
        dto.put("connexionPermanente", membre.getConnexionPermanente());
        dto.put("bailHeure", membre.getBailHeure());
        return dto;
    }

    // ── Modifier permissions (par le propriétaire uniquement) ──
    @Transactional
    public Map<String, Object> modifierPermissions(
            String membreUuid, Map<String, Boolean> body, String telephoneAuteur) {

        MembreGroupe membre = membreRepo.findByUuid(membreUuid)
                .orElseThrow(() -> new RuntimeException("Membre introuvable"));

        // Sécurité : seul le propriétaire du groupe auquel appartient
        // ce membre a le droit de modifier ses permissions. Avant,
        // n'importe quel utilisateur authentifié pouvait appeler cet
        // endpoint pour n'importe quel membre de n'importe quel groupe.
        Groupe groupe = membre.getGroupe();
        if (!telephonesEquivalents(groupe.getProprietaire().getTelephone(), telephoneAuteur)) {
            throw new RuntimeException(
                    "Seul le propriétaire peut modifier les permissions");
        }

        PermissionMembre p = permissionRepo
                .findByMembreId(membre.getId())
                .orElseGet(() -> PermissionMembre.builder()
                        .membre(membre)
                        .peutVendre(true)
                        .peutVoirDettes(false)
                        .peutGererStock(false)
                        .peutVoirStats(false)
                        .peutGererClients(false)
                        .peutVoirHistorique(false)
                        .build());

        if (body.containsKey("peutVendre"))
            p.setPeutVendre(body.get("peutVendre"));
        if (body.containsKey("peutVoirDettes"))
            p.setPeutVoirDettes(body.get("peutVoirDettes"));
        if (body.containsKey("peutGererStock"))
            p.setPeutGererStock(body.get("peutGererStock"));
        if (body.containsKey("peutVoirStats"))
            p.setPeutVoirStats(body.get("peutVoirStats"));
        if (body.containsKey("peutGererClients"))
            p.setPeutGererClients(body.get("peutGererClients"));
        if (body.containsKey("peutVoirHistorique"))
            p.setPeutVoirHistorique(body.get("peutVoirHistorique"));

        permissionRepo.save(p);
        Map<String, Object> dto = buildPermissionsDto(p);
        dto.put("groupeUuid", groupe.getUuid());
        dto.put("membreUuid", membre.getUuid());
        return dto;
    }

    // ── Passer en mode multi / solo ───────────────────────────
    @Transactional
    public Map<String, Object> passerEnModeMulti(String groupeUuid) {
        Groupe g = groupeRepo.findByUuid(groupeUuid)
                .orElseThrow(() -> new RuntimeException("Groupe introuvable"));
        g.setMode("multi");
        groupeRepo.save(g);
        return Map.of("groupeUuid", groupeUuid, "mode", "multi");
    }

    @Transactional
    public Map<String, Object> passerEnModeSolo(String groupeUuid) {
        Groupe g = groupeRepo.findByUuid(groupeUuid)
                .orElseThrow(() -> new RuntimeException("Groupe introuvable"));
        g.setMode("solo");
        groupeRepo.save(g);
        return Map.of("groupeUuid", groupeUuid, "mode", "solo");
    }

    public String getMode(String groupeUuid) {
        return groupeRepo.findByUuid(groupeUuid)
                .map(Groupe::getMode).orElse("solo");
    }

    // ── Helpers ───────────────────────────────────────────────
    private void verifierPermission(Long membreId, String type) {
        PermissionMembre p = permissionRepo
                .findByMembreId(membreId).orElse(null);

        // BUG CORRIGÉ : une ligne de permissions manquante (ex. membre
        // synchronisé via SyncService avant le correctif ci-dessus,
        // ou tout autre chemin de création oublié à l'avenir) faisait
        // échouer TOUTE opération — vente, stock, dettes, contacts —
        // avec "Aucune permission définie", y compris pour le
        // propriétaire. On s'auto-répare maintenant ici exactement
        // comme le fait déjà getPermissions() (même défaut : accès
        // complet pour un propriétaire, peutVendre seul sinon),
        // plutôt que de bloquer silencieusement l'utilisateur sans
        // qu'aucun écran ne lui permette de corriger la situation.
        if (p == null) {
            MembreGroupe m = membreRepo.findById(membreId).orElse(null);
            boolean estProprietaire = m != null && "proprietaire".equals(m.getRole());
            p = PermissionMembre.builder()
                    .membre(m)
                    .peutVendre(true)
                    .peutVoirDettes(estProprietaire)
                    .peutGererStock(estProprietaire)
                    .peutVoirStats(estProprietaire)
                    .peutGererClients(estProprietaire)
                    .peutVoirHistorique(estProprietaire)
                    .build();
            p = permissionRepo.save(p);
        }

        boolean ok = switch (type != null ? type : "") {
            case "vente"             -> p.getPeutVendre();
            case "mouvement_stock"   -> p.getPeutGererStock();
            case "client"            -> p.getPeutGererClients();
            // Pas de permission dédiée "fournisseurs" dans le modèle
            // actuel : rattachée à la gestion du stock, cohérent avec
            // l'écran Stock qui gère aussi les fournisseurs par défaut.
            case "fournisseur"       -> p.getPeutGererClients();
            case "produit"           -> p.getPeutGererStock();
            // Idem : pas de permission "gérer les dettes" dédiée ; on
            // s'appuie sur peutVoirDettes (seule permission liée aux
            // dettes existante aujourd'hui).
            case "dette"                            -> p.getPeutVoirDettes();
            case "dette_fournisseur"                -> p.getPeutVoirDettes();
            case "dette_remboursement" -> p.getPeutVoirDettes();
            case "dette_fournisseur_remboursement" -> p.getPeutVoirDettes();
            default                  -> false;
        };
        if (!ok)
            throw new RuntimeException(
                    "Permission refusée pour: " + type);
    }

    private Map<String, Object> buildPermissionsDto(PermissionMembre p) {
        if (p == null) return Map.of("peutVendre", false);
        Map<String, Object> dto = new HashMap<>();
        dto.put("peutVendre",         p.getPeutVendre());
        dto.put("peutVoirDettes",     p.getPeutVoirDettes());
        dto.put("peutGererStock",     p.getPeutGererStock());
        dto.put("peutVoirStats",      p.getPeutVoirStats());
        dto.put("peutGererClients",   p.getPeutGererClients());
        dto.put("peutVoirHistorique", p.getPeutVoirHistorique());
        return dto;
    }

    private String s(Map<String,Object> m, String k) {
        Object v = m.get(k); return v != null ? v.toString() : null;
    }

    private Double d(Map<String,Object> m, String k) {
        Object v = m.get(k);
        return v != null ? Double.parseDouble(v.toString()) : null;
    }

    // Voir GroupeService.estReellementConnecte : basé sur une session
    // WebSocket ouverte pour ce membre sur ce groupe, plus fiable que
    // l'ancien seuil de fraîcheur de heartbeat.
    private boolean estReellementConnecte(MembreGroupe m) {
        if (m.getGroupe() == null || m.getUtilisateur() == null) return false;
        return groupeWebSocketHandler.estConnecte(
                m.getGroupe().getUuid(), m.getUtilisateur().getId());
    }
}