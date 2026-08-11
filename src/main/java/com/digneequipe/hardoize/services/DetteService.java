package com.digneequipe.hardoize.services;

import com.digneequipe.hardoize.models.*;
import com.digneequipe.hardoize.repositories.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import java.time.*;
import java.util.*;

@Service
@RequiredArgsConstructor
public class DetteService {

    private final DetteRepository   detteRepo;
    private final ClientRepository  clientRepo;
    private final VenteRepository   venteRepo;
    private final GroupeRepository  groupeRepo;
    private final UtilisateurRepository utilisateurRepo;
    private final HistoriquePaiementRepository historiquePaiementRepo;

    @Transactional
    public Map<String, Object> creerOuMaj(Map<String, Object> body) {
        String uuid = s(body, "uuid");
        if (uuid == null) throw new RuntimeException("UUID obligatoire");

        Dette d = detteRepo.findByUuid(uuid)
                .orElse(Dette.builder().uuid(uuid).build());

        d.setMontantTotal(dOrZero(body, "montantTotal"));
        d.setMontantRembourse(dOrZero(body, "montantRembourse"));
        d.setMontantRestant(
                body.containsKey("montantRestant")
                        ? dOrZero(body, "montantRestant")
                        : d.getMontantTotal() - d.getMontantRembourse()
        );
        d.setStatut(s(body, "statut") != null
                ? s(body, "statut") : "active");
        d.setPaiementsJson(s(body, "paiementsJson"));

        // DateRemboursement
        if (body.containsKey("dateRemboursement")
                && body.get("dateRemboursement") != null) {
            try {
                long ms = Long.parseLong(
                        body.get("dateRemboursement").toString());
                // IMPORTANT : ZoneOffset.UTC, jamais ZoneId.systemDefault()
                // — cf. BaseEntity.onCreate : toute date stockée côté
                // serveur doit représenter de l'UTC, sans quoi l'échéance
                // se retrouve décalée par rapport à ce que voient les
                // autres appareils du même groupe.
                d.setDateRemboursement(LocalDateTime.ofInstant(
                        Instant.ofEpochMilli(ms), ZoneOffset.UTC));
            } catch (NumberFormatException e) {
                // Ignorer
            }
        }

        String vUuid = s(body, "venteUuid");
        if (vUuid != null)
            venteRepo.findByUuid(vUuid).ifPresent(d::setVente);

        String cUuid = s(body, "clientUuid");
        if (cUuid != null)
            clientRepo.findByUuid(cUuid).ifPresent(d::setClient);

        String gUuid = s(body, "groupeUuid");
        if (gUuid != null)
            groupeRepo.findByUuid(gUuid).ifPresent(d::setGroupe);

        d = detteRepo.save(d);
        return buildDto(d);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getByGroupe(Long groupeId) {
        List<Dette> dettes = detteRepo.findByGroupeId(groupeId);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Dette d : dettes) result.add(buildDto(d));
        return result;
    }

    @Transactional
    public Map<String, Object> rembourser(String uuid, double montant, String telephoneAuteur) {
        Dette d = detteRepo.findByUuid(uuid)
                .orElseThrow(() -> new RuntimeException("Dette introuvable"));

        d.setMontantRembourse(d.getMontantRembourse() + montant);
        d.setMontantRestant(
                Math.max(0, d.getMontantTotal() - d.getMontantRembourse()));
        if (d.getMontantRestant() <= 0) {
            d.setStatut("soldee");
            d.setDateSolde(LocalDateTime.now(ZoneOffset.UTC));
        }

        d = detteRepo.save(d);
        Map<String, Object> dto = buildDto(d);

        // BUG CORRIGÉ : le bonus de score (+10) à la clôture d'une
        // dette était géré CÔTÉ CLIENT via ClientDB.incrementerScore,
        // appelé SANS CONDITION même en mode multi — une écriture
        // locale directe qui (comme pour toggleActivation) posait
        // syncEnAttente=1 sur ce client, sur CET appareil, pour
        // toujours : plus aucune future mise à jour serveur de ce
        // client n'était alors appliquée localement, ni la
        // synchronisation du score vers les autres membres. Le bonus
        // est désormais calculé ici, seule source de vérité, et
        // renvoyé pour que chaque appareil applique la même valeur.
        if ("soldee".equals(d.getStatut()) && d.getClient() != null) {
            Client c = d.getClient();
            int nouveauScore = Math.min(100, (c.getScore() != null ? c.getScore() : 100) + 10);
            c.setScore(nouveauScore);
            clientRepo.save(c);
            dto.put("clientScore", nouveauScore);
        }

        // BUG CORRIGÉ : cette méthode ne persistait JAMAIS de ligne
        // d'historique de paiement côté serveur — seule l'écriture
        // locale faite par chaque appareil au moment de l'écho
        // WebSocket existait. Un appareil hors ligne ou déconnecté
        // pile à ce moment-là ne recevait donc CE paiement précis
        // NULLE PART, y compris lors d'une resynchronisation complète
        // ultérieure (puisque le serveur n'avait rien à renvoyer). Le
        // paiement est désormais la source de vérité côté serveur, et
        // son uuid réel est renvoyé pour que le frontend s'en serve
        // (au lieu de fabriquer sa propre clé de déduplication).
        String nomAuteur = utilisateurRepo.findByTelephone(telephoneAuteur)
                .map(Utilisateur::getNom).orElse(null);

        HistoriquePaiement hp = HistoriquePaiement.builder()
                .type("client")
                .sens("entrant")
                .montant(montant)
                .description("Remboursement dette — " +
                        (d.getClient() != null ? d.getClient().getNomClient() : ""))
                .nomClient(d.getClient() != null ? d.getClient().getNomClient() : null)
                .client(d.getClient())
                .dette(d)
                .groupe(d.getGroupe())
                .nomUtilisateur(nomAuteur)
                .build();
        hp = historiquePaiementRepo.save(hp);

        dto.put("nomUtilisateur", nomAuteur);
        dto.put("historiquePaiementUuid", hp.getUuid());
        return dto;
    }

    private Map<String, Object> buildDto(Dette d) {
        Map<String, Object> dto = new HashMap<>();
        dto.put("id",              d.getId());
        dto.put("uuid",            d.getUuid());
        dto.put("montantTotal",    d.getMontantTotal());
        dto.put("montantRembourse",d.getMontantRembourse());
        dto.put("montantRestant",  d.getMontantRestant());
        dto.put("statut",          d.getStatut());
        dto.put("clientUuid",      d.getClient() != null
                ? d.getClient().getUuid() : null);
        dto.put("venteUuid",       d.getVente() != null
                ? d.getVente().getUuid() : null);
        dto.put("groupeUuid",      d.getGroupe() != null
                ? d.getGroupe().getUuid() : null);
        dto.put("createdAt",       d.getCreatedAt());
        // Manquait jusqu'ici : sans ce champ dans le DTO, l'échéance
        // n'était tout simplement jamais transmise aux autres appareils
        // du groupe (ni via le poll 30s, ni via la sync initiale) — ils
        // recevaient la dette mais avec une échéance absente/nulle.
        dto.put("dateRemboursement", d.getDateRemboursement());
        dto.put("dateSolde",         d.getDateSolde());
        return dto;
    }

    private String s(Map<String,Object> m, String k) {
        Object v = m.get(k); return v != null ? v.toString() : null;
    }
    private double dOrZero(Map<String,Object> m, String k) {
        Object v = m.get(k);
        return v != null ? Double.parseDouble(v.toString()) : 0.0;
    }
}