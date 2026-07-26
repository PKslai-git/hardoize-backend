package com.digneequipe.hardoize.services;

import com.digneequipe.hardoize.models.*;
import com.digneequipe.hardoize.repositories.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
public class GroupeService {

    private final GroupeRepository         groupeRepo;
    private final MembreGroupeRepository   membreRepo;
    private final PermissionMembreRepository permissionRepo;
    private final UtilisateurRepository    utilisateurRepo;

    @Transactional
    public Map<String, Object> creer(Map<String, Object> body,
                                     String telephone) {
        Utilisateur proprietaire = utilisateurRepo
                .findByTelephone(telephone)
                .orElseThrow(() -> new RuntimeException("Utilisateur introuvable"));

        Groupe groupe = Groupe.builder()
                .uuid(body.get("uuid") != null
                        ? body.get("uuid").toString()
                        : UUID.randomUUID().toString())
                .nom(body.get("nom").toString())
                .description(body.containsKey("description")
                        ? body.get("description") != null
                        ? body.get("description").toString() : null
                        : null)
                .codeQR(body.containsKey("codeQR")
                        ? body.get("codeQR").toString()
                        : UUID.randomUUID().toString())
                .heureFermeture(body.containsKey("heureFermeture")
                        ? body.get("heureFermeture").toString() : "18:00")
                .mode("solo")
                .proprietaire(proprietaire)
                .estActif(true)
                .build();

        groupe = groupeRepo.save(groupe);

        // Propriétaire comme membre
        MembreGroupe membre = MembreGroupe.builder()
                .groupe(groupe)
                .utilisateur(proprietaire)
                .nomAffiche(proprietaire.getNom())
                .telephone(proprietaire.getTelephone())
                .role("proprietaire")
                .bailHeure(groupe.getHeureFermeture())
                .estConnecte(true)
                .connexionPermanente(true)
                .build();
        membre = membreRepo.save(membre);

        // Permissions propriétaire (tout)
        PermissionMembre perms = PermissionMembre.builder()
                .membre(membre)
                .peutVendre(true)
                .peutVoirDettes(true)
                .peutGererStock(true)
                .peutVoirStats(true)
                .peutGererClients(true)
                .peutVoirHistorique(true)
                .build();
        permissionRepo.save(perms);

        return buildDto(groupe);
    }

    @Transactional
    public Map<String, Object> modifier(Long id,
                                        Map<String, Object> body) {
        Groupe groupe = groupeRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Groupe introuvable"));

        if (body.containsKey("nom"))
            groupe.setNom(body.get("nom").toString());
        if (body.containsKey("description"))
            groupe.setDescription(body.get("description") != null
                    ? body.get("description").toString() : null);
        if (body.containsKey("heureFermeture"))
            groupe.setHeureFermeture(
                    body.get("heureFermeture").toString());

        groupe = groupeRepo.save(groupe);
        return buildDto(groupe);
    }

    public List<Map<String, Object>> getByProprietaire(String telephone) {
        Utilisateur user = utilisateurRepo
                .findByTelephone(telephone)
                .orElseThrow(() -> new RuntimeException("Utilisateur introuvable"));

        List<Groupe> groupes = groupeRepo
                .findByProprietaireId(user.getId());

        List<Map<String, Object>> result = new ArrayList<>();
        for (Groupe g : groupes) result.add(buildDto(g));
        return result;
    }

    public List<Map<String, Object>> getMembres(Long groupeId) {
        List<MembreGroupe> membres =
                membreRepo.findByGroupeId(groupeId);

        List<Map<String, Object>> result = new ArrayList<>();
        for (MembreGroupe m : membres) {
            Map<String, Object> dto = new HashMap<>();
            dto.put("id",          m.getId());
            dto.put("uuid",        m.getUuid());
            dto.put("nomAffiche",  m.getNomAffiche());
            dto.put("telephone",   m.getTelephone());
            dto.put("role",        m.getRole());
            dto.put("bailHeure",   m.getBailHeure());
            dto.put("estConnecte", m.getEstConnecte());
            dto.put("connexionPermanente", m.getConnexionPermanente());

            permissionRepo.findByMembreId(m.getId()).ifPresent(p -> {
                Map<String, Object> perms = new HashMap<>();
                perms.put("peutVendre",         p.getPeutVendre());
                perms.put("peutVoirDettes",     p.getPeutVoirDettes());
                perms.put("peutGererStock",     p.getPeutGererStock());
                perms.put("peutVoirStats",      p.getPeutVoirStats());
                perms.put("peutGererClients",   p.getPeutGererClients());
                perms.put("peutVoirHistorique", p.getPeutVoirHistorique());
                dto.put("permissions", perms);
            });

            result.add(dto);
        }
        return result;
    }

    private Map<String, Object> buildDto(Groupe g) {
        Map<String, Object> dto = new HashMap<>();
        dto.put("id",             g.getId());
        dto.put("uuid",           g.getUuid());
        dto.put("nom",            g.getNom());
        dto.put("description",    g.getDescription());
        dto.put("codeQR",         g.getCodeQR());
        dto.put("heureFermeture", g.getHeureFermeture());
        dto.put("mode",           g.getMode());
        dto.put("createdAt",      g.getCreatedAt());
        return dto;
    }

    // Dans GroupeService.java, ajoute ces méthodes :

    @Transactional
    public Map<String, Object> modifierParUuid(
            String uuid, Map<String, Object> body, String telephone) {

        Groupe groupe = groupeRepo.findByUuid(uuid)
                .orElseThrow(() -> new RuntimeException("Groupe introuvable"));

        // Vérifier que c'est bien le propriétaire
        Utilisateur user = utilisateurRepo.findByTelephone(telephone)
                .orElseThrow(() -> new RuntimeException("Utilisateur introuvable"));

        if (!groupe.getProprietaire().getId().equals(user.getId())) {
            throw new RuntimeException("Accès refusé");
        }

        if (body.containsKey("nom"))
            groupe.setNom(body.get("nom").toString());
        if (body.containsKey("description"))
            groupe.setDescription(body.get("description") != null
                    ? body.get("description").toString() : null);
        if (body.containsKey("heureFermeture"))
            groupe.setHeureFermeture(body.get("heureFermeture").toString());

        groupe = groupeRepo.save(groupe);
        return buildDto(groupe);
    }

    public List<Map<String, Object>> getMembresParUuid(
            String groupeUuid, String telephone) {

        Groupe groupe = groupeRepo.findByUuid(groupeUuid)
                .orElseThrow(() -> new RuntimeException("Groupe introuvable"));

        // Vérifier accès : propriétaire ou membre du groupe
        Utilisateur user = utilisateurRepo.findByTelephone(telephone)
                .orElseThrow(() -> new RuntimeException("Utilisateur introuvable"));

        boolean autorise = groupe.getProprietaire().getId().equals(user.getId())
                || membreRepo.findByGroupeIdAndTelephone(
                groupe.getId(), telephone).isPresent();

        if (!autorise) throw new RuntimeException("Accès refusé");

        List<MembreGroupe> membres = membreRepo.findByGroupeId(groupe.getId());
        List<Map<String, Object>> result = new ArrayList<>();

        for (MembreGroupe m : membres) {
            Map<String, Object> dto = new HashMap<>();
            dto.put("id",               m.getId());
            dto.put("uuid",             m.getUuid());   // ← UUID
            dto.put("groupeUuid",       groupe.getUuid()); // ← UUID groupe
            dto.put("nomAffiche",       m.getNomAffiche());
            dto.put("telephone",        m.getTelephone());
            dto.put("role",             m.getRole());
            dto.put("bailHeure",        m.getBailHeure());
            dto.put("estConnecte",      m.getEstConnecte());
            dto.put("connexionPermanente", m.getConnexionPermanente());

            permissionRepo.findByMembreId(m.getId()).ifPresent(p -> {
                Map<String, Object> perms = new HashMap<>();
                perms.put("peutVendre",         p.getPeutVendre());
                perms.put("peutVoirDettes",     p.getPeutVoirDettes());
                perms.put("peutGererStock",     p.getPeutGererStock());
                perms.put("peutVoirStats",      p.getPeutVoirStats());
                perms.put("peutGererClients",   p.getPeutGererClients());
                perms.put("peutVoirHistorique", p.getPeutVoirHistorique());
                dto.put("permissions", perms);
            });

            result.add(dto);
        }
        return result;
    }

    @Transactional
    public void retirerMembreParUuid(String membreUuid, String telephone) {
        MembreGroupe membre = membreRepo.findByUuid(membreUuid)
                .orElseThrow(() -> new RuntimeException("Membre introuvable"));

        // Seul le propriétaire peut retirer un membre
        Groupe groupe = membre.getGroupe();
        if (!groupe.getProprietaire().getTelephone().equals(telephone)) {
            throw new RuntimeException("Seul le propriétaire peut retirer un membre");
        }

        // Ne pas retirer le propriétaire
        if ("proprietaire".equals(membre.getRole())) {
            throw new RuntimeException("Impossible de retirer le propriétaire");
        }

        membre.setEstActif(false);
        membre.setEstConnecte(false);
        membreRepo.save(membre);

        // Si plus aucun vendeur connecté → repasser en solo
        long nbVendeurs = membreRepo.findByGroupeId(groupe.getId())
                .stream()
                .filter(m -> m.getEstActif() && !"proprietaire".equals(m.getRole()))
                .count();

        if (nbVendeurs == 0) {
            groupe.setMode("solo");
            groupeRepo.save(groupe);
        }
    }
}