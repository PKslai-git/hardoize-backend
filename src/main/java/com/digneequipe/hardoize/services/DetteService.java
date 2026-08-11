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
    private final HistoriqueService historiqueService;

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
    public Map<String, Object> rembourser(String uuid, double montant, String telephone, String operationUuid) {
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

        // Historique paiement — manquait entièrement ici jusqu'ici :
        // rembourser() ne touchait que la Dette elle-même, aucune ligne
        // n'était jamais créée dans historique_paiements côté serveur
        // (seule une insertion locale ad-hoc existait côté app, sujette
        // au bug de dédoublonnage WebSocket corrigé par ailleurs). Le
        // nom de l'auteur est résolu par téléphone, comme pour
        // MouvementStock.nomUtilisateur. L'uuid du paiement réutilise
        // l'operationUuid de CETTE opération (unique par appel, déjà
        // plombé de bout en bout depuis le correctif websocketManager)
        // plutôt qu'une clé recomposée à partir du montant — évite tout
        // risque de désaccord de format entre Java et JS pour la même
        // valeur, qui aurait pu dupliquer la ligne lors d'un rattrapage
        // de reconnexion.
        String nomAuteur = utilisateurRepo.findByTelephone(telephone)
                .map(Utilisateur::getNom).orElse(null);
        if (montant > 0) {
            Map<String, Object> paiementBody = new HashMap<>();
            paiementBody.put("uuid",
                    operationUuid != null ? operationUuid
                            : "remb-client-" + uuid + "-" + d.getMontantRembourse());
            paiementBody.put("type", "client");
            paiementBody.put("sens", "entrant");
            paiementBody.put("montant", montant);
            paiementBody.put("description",
                    "Remboursement dette — " + (d.getClient() != null ? d.getClient().getNomClient() : ""));
            paiementBody.put("nomClient",
                    d.getClient() != null ? d.getClient().getNomClient() : null);
            paiementBody.put("nomUtilisateur", nomAuteur);
            paiementBody.put("clientUuid",
                    d.getClient() != null ? d.getClient().getUuid() : null);
            paiementBody.put("groupeUuid",
                    d.getGroupe() != null ? d.getGroupe().getUuid() : null);
            historiqueService.enregistrerPaiement(paiementBody);
        }

        Map<String, Object> dto = buildDto(d);
        dto.put("nomUtilisateur", nomAuteur);
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