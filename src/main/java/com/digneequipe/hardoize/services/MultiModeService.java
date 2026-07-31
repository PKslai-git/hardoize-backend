package com.digneequipe.hardoize.services;

import com.digneequipe.hardoize.models.*;
import com.digneequipe.hardoize.repositories.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
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

    // ── Rejoindre un groupe via QR ─────────────────────────────
    @Transactional
    public Map<String, Object> rejoindreGroupe(
            String codeQR, String telephone, String nomAffiche) {

        Groupe groupe = groupeRepo.findByCodeQR(codeQR)
                .orElseThrow(() ->
                        new RuntimeException("Code QR invalide ou expiré"));

        Utilisateur user = utilisateurRepo
                .findByTelephone(telephone)
                .orElseThrow(() ->
                        new RuntimeException("Utilisateur introuvable"));

        // Vérifier si déjà membre — et s'il était déjà connecté avant
        // ce scan (pour donner un message adapté côté client : "déjà
        // connecté" plutôt que de refaire toute la reconnexion en
        // silence, ou l'inverse).
        final boolean[] etaitDejaConnecte = { false };
        MembreGroupe membre = membreRepo
                .findByGroupeIdAndTelephone(groupe.getId(), telephone)
                .map(m -> {
                    etaitDejaConnecte[0] = Boolean.TRUE.equals(m.getEstConnecte());
                    m.setEstConnecte(true);
                    if (nomAffiche != null) m.setNomAffiche(nomAffiche);
                    return membreRepo.save(m);
                })
                .orElseGet(() -> {
                    MembreGroupe m = MembreGroupe.builder()
                            .groupe(groupe)
                            .utilisateur(user)
                            .nomAffiche(nomAffiche != null
                                    ? nomAffiche : user.getNom())
                            .telephone(telephone)
                            .role("vendeur")
                            .bailHeure(groupe.getHeureFermeture())
                            .estConnecte(true)
                            .connexionPermanente(false)
                            .build();
                    m = membreRepo.save(m);

                    // Permissions par défaut
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

        // Passer en mode multi si 2+ membres
        long nbMembres = membreRepo.countByGroupeId(groupe.getId());
        if (nbMembres > 1) {
            groupe.setMode("multi");
            groupeRepo.save(groupe);
        }

        // Charger les permissions
        PermissionMembre perms = permissionRepo
                .findByMembreId(membre.getId()).orElse(null);

        // Correction : on ne renvoie plus les ids numériques (membreId,
        // groupeId) — ils sont propres à la base serveur et ne
        // correspondent à rien côté SQLite local. Seuls les uuid,
        // identiques partout, doivent être utilisés par l'app.
        Map<String, Object> result = new HashMap<>();
        result.put("membreUuid",  membre.getUuid());
        result.put("groupeUuid",  groupe.getUuid());
        result.put("groupeNom",   groupe.getNom());
        result.put("mode",        groupe.getMode());
        result.put("bailHeure",   membre.getBailHeure());
        result.put("permissions", buildPermissionsDto(perms));
        result.put("dejaConnecte", etaitDejaConnecte[0]);
        return result;
    }

    // ── Polling sync 30s ──────────────────────────────────────
    public Map<String, Object> getSyncData(String groupeUuid, String depuis) {
        Groupe groupe = groupeRepo.findByUuid(groupeUuid)
                .orElseThrow(() -> new RuntimeException("Groupe introuvable"));

        Map<String, Object> data = new HashMap<>();
        data.put("groupeUuid", groupe.getUuid());
        data.put("mode",       groupe.getMode());
        data.put("timestamp",  LocalDateTime.now(ZoneOffset.UTC).toString());
        data.put("ok",         true);
        return data;
    }

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
            case "dette_remboursement" -> {
                @SuppressWarnings("unchecked")
                Map<String, Object> data =
                        (Map<String, Object>) payload.get("data");
                if (data == null) data = payload;
                String detteUuid = s(data, "uuid");
                if (detteUuid == null)
                    throw new RuntimeException("uuid de la dette manquant");
                double montant = d(data, "montant") != null ? d(data, "montant") : 0.0;
                yield detteService.rembourser(detteUuid, montant);
            }
            default -> throw new RuntimeException(
                    "Type d'opération non supporté: " + type);
        };
    }

    // ── Dashboard propriétaire ────────────────────────────────
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
            dto.put("estConnecte", m.getEstConnecte());
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
        return dto;
    }

    // ── Signal de présence (heartbeat) ────────────────────────
    // Marque le membre comme connecté. Contrairement à l'ancien
    // fonctionnement, où estConnecte n'était mis à jour qu'à la
    // jointure initiale (donc figé "vrai" pour toujours ensuite,
    // même après fermeture de l'app), cette méthode est appelée à
    // chaque poll 30s tant que l'app est active — estConnecte
    // reflète donc une activité réelle et récente.
    @Transactional
    public void marquerConnecte(String membreUuid) {
        membreRepo.findByUuid(membreUuid).ifPresent(m -> {
            if (!Boolean.TRUE.equals(m.getEstConnecte())) {
                m.setEstConnecte(true);
                membreRepo.save(m);
            }
        });
    }

    // ── Déconnecter un membre ─────────────────────────────────
    @Transactional
    public void deconnecterMembre(String membreUuid) {
        membreRepo.findByUuid(membreUuid).ifPresent(m -> {
            m.setEstConnecte(false);
            membreRepo.save(m);

            long nbConnectes = membreRepo
                    .findByGroupeId(m.getGroupe().getId())
                    .stream()
                    .filter(mb -> mb.getEstConnecte()
                            && !"proprietaire".equals(mb.getRole()))
                    .count();

            if (nbConnectes == 0) {
                Groupe g = m.getGroupe();
                g.setMode("solo");
                groupeRepo.save(g);
            }
        });
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
    }

    // ── Déconnexion forcée par le propriétaire ─────────────────
    @Transactional
    public void deconnecterMembreParProprietaire(
            String membreUuid, String telephoneAuteur) {
        MembreGroupe membre = membreRepo.findByUuid(membreUuid)
                .orElseThrow(() -> new RuntimeException("Membre introuvable"));
        verifierEstProprietaire(membre, telephoneAuteur);
        deconnecterMembre(membreUuid);
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
        if (a == null || b == null) return false;
        String na = a.replaceAll("[^0-9]", "");
        String nb = b.replaceAll("[^0-9]", "");
        // Comparer sur les 8 derniers chiffres (numéro local, sans
        // indicatif pays) suffit à identifier la même ligne.
        int len = Math.min(na.length(), nb.length());
        int taille = Math.min(len, 8);
        if (taille == 0) return na.equals(nb);
        return na.substring(na.length() - taille)
                 .equals(nb.substring(nb.length() - taille));
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
        PermissionMembre p = permissionRepo
                .findByMembreId(membre.getId())
                .orElseGet(() -> {
                    PermissionMembre def = PermissionMembre.builder()
                            .membre(membre)
                            .peutVendre(true)
                            .peutVoirDettes(false)
                            .peutGererStock(false)
                            .peutVoirStats(false)
                            .peutGererClients(false)
                            .peutVoirHistorique(false)
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
        return buildPermissionsDto(p);
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

        // Absence de ligne de permissions = accès refusé par défaut
        // (ne devrait normalement jamais arriver : une ligne est créée
        // pour chaque membre dès qu'il rejoint un groupe — voir
        // rejoindreGroupe). Avant : `return;` ici laissait passer
        // l'opération silencieusement, à l'inverse de ce que dit ce
        // commentaire.
        if (p == null)
            throw new RuntimeException(
                    "Aucune permission définie pour ce membre");

        boolean ok = switch (type != null ? type : "") {
            case "vente"             -> p.getPeutVendre();
            case "mouvement_stock"   -> p.getPeutGererStock();
            case "client"            -> p.getPeutGererClients();
            // Pas de permission dédiée "fournisseurs" dans le modèle
            // actuel : rattachée à la gestion du stock, cohérent avec
            // l'écran Stock qui gère aussi les fournisseurs par défaut.
            case "fournisseur"       -> p.getPeutGererStock();
            // Idem : pas de permission "gérer les dettes" dédiée ; on
            // s'appuie sur peutVoirDettes (seule permission liée aux
            // dettes existante aujourd'hui).
            case "dette_remboursement" -> p.getPeutVoirDettes();
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
}