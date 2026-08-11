package com.digneequipe.hardoize.services;

import com.digneequipe.hardoize.models.*;
import com.digneequipe.hardoize.repositories.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;

@Service
@RequiredArgsConstructor
public class DetteFournisseurService {

    private final DetteFournisseurRepository detteFournRepo;
    private final FournisseurRepository      fournisseurRepo;
    private final GroupeRepository           groupeRepo;
    private final UtilisateurRepository      utilisateurRepo;
    private final HistoriqueService          historiqueService;

    @Transactional
    public Map<String, Object> creerOuMaj(Map<String, Object> body) {
        String uuid = s(body, "uuid");
        if (uuid == null) throw new RuntimeException("UUID obligatoire");

        DetteFournisseur df = detteFournRepo.findByUuid(uuid)
                .orElse(DetteFournisseur.builder().uuid(uuid).build());

        df.setNomFournisseur(s(body, "nomFournisseur"));
        df.setMontantTotal(dz(body, "montantTotal"));
        df.setMontantRembourse(dz(body, "montantRembourse"));
        df.setMontantRestant(
                body.containsKey("montantRestant")
                        ? dz(body, "montantRestant")
                        : df.getMontantTotal() - df.getMontantRembourse()
        );
        df.setMotif(s(body, "motif"));
        df.setStatut(s(body, "statut") != null
                ? s(body, "statut") : "active");
        df.setPaiementsJson(s(body, "paiementsJson"));

        if (body.containsKey("dateRemboursement")
                && body.get("dateRemboursement") != null) {
            try {
                long ms = Long.parseLong(
                        body.get("dateRemboursement").toString());
                df.setDateRemboursement(LocalDateTime.ofInstant(
                        Instant.ofEpochMilli(ms), ZoneId.systemDefault()));
            } catch (NumberFormatException ignored) {}
        }

        String fUuid = s(body, "fournisseurUuid");
        if (fUuid != null)
            fournisseurRepo.findByUuid(fUuid).ifPresent(df::setFournisseur);

        String gUuid = s(body, "groupeUuid");
        if (gUuid != null)
            groupeRepo.findByUuid(gUuid).ifPresent(df::setGroupe);

        df = detteFournRepo.save(df);
        return buildDto(df);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getByGroupe(Long groupeId) {
        List<DetteFournisseur> list =
                detteFournRepo.findByGroupeId(groupeId);
        List<Map<String, Object>> result = new ArrayList<>();
        for (DetteFournisseur df : list) result.add(buildDto(df));
        return result;
    }

    @Transactional
    public Map<String, Object> rembourser(String uuid, double montant, String telephone, String operationUuid) {
        DetteFournisseur df = detteFournRepo.findByUuid(uuid)
                .orElseThrow(() ->
                        new RuntimeException("Dette fournisseur introuvable"));

        df.setMontantRembourse(df.getMontantRembourse() + montant);
        df.setMontantRestant(
                Math.max(0, df.getMontantTotal() - df.getMontantRembourse()));
        if (df.getMontantRestant() <= 0) {
            df.setStatut("soldee");
        }
        df = detteFournRepo.save(df);

        // Historique paiement — même correctif que DetteService.rembourser :
        // aucune ligne n'était jamais créée côté serveur pour un
        // remboursement fournisseur.
        String nomAuteur = utilisateurRepo.findByTelephone(telephone)
                .map(Utilisateur::getNom).orElse(null);
        if (montant > 0) {
            Map<String, Object> paiementBody = new HashMap<>();
            paiementBody.put("uuid",
                    operationUuid != null ? operationUuid
                            : "remb-fourn-" + uuid + "-" + df.getMontantRembourse());
            paiementBody.put("type", "fournisseur");
            paiementBody.put("sens", "sortant");
            paiementBody.put("montant", montant);
            paiementBody.put("description",
                    "Remboursement fournisseur — " + (df.getFournisseur() != null ? df.getFournisseur().getNom() : df.getNomFournisseur()));
            paiementBody.put("nomFournisseur",
                    df.getFournisseur() != null ? df.getFournisseur().getNom() : df.getNomFournisseur());
            paiementBody.put("nomUtilisateur", nomAuteur);
            paiementBody.put("fournisseurUuid",
                    df.getFournisseur() != null ? df.getFournisseur().getUuid() : null);
            paiementBody.put("groupeUuid",
                    df.getGroupe() != null ? df.getGroupe().getUuid() : null);
            historiqueService.enregistrerPaiement(paiementBody);
        }

        Map<String, Object> dto = buildDto(df);
        dto.put("nomUtilisateur", nomAuteur);
        return dto;
    }

    @Transactional
    public Map<String, Object> solder(String uuid) {
        DetteFournisseur df = detteFournRepo.findByUuid(uuid)
                .orElseThrow(() ->
                        new RuntimeException("Dette fournisseur introuvable"));
        df.setMontantRembourse(df.getMontantTotal());
        df.setMontantRestant(0.0);
        df.setStatut("soldee");
        df = detteFournRepo.save(df);
        return buildDto(df);
    }

    private Map<String, Object> buildDto(DetteFournisseur df) {
        Map<String, Object> dto = new HashMap<>();
        dto.put("id",              df.getId());
        dto.put("uuid",            df.getUuid());
        dto.put("nomFournisseur",  df.getNomFournisseur());
        dto.put("montantTotal",    df.getMontantTotal());
        dto.put("montantRembourse",df.getMontantRembourse());
        dto.put("montantRestant",  df.getMontantRestant());
        dto.put("statut",          df.getStatut());
        dto.put("motif",           df.getMotif());
        dto.put("fournisseurUuid", df.getFournisseur() != null
                ? df.getFournisseur().getUuid() : null);
        dto.put("groupeUuid",      df.getGroupe() != null
                ? df.getGroupe().getUuid() : null);
        dto.put("createdAt",       df.getCreatedAt());
        dto.put("dateRemboursement", df.getDateRemboursement());
        return dto;
    }

    private String s(Map<String,Object> m, String k) {
        Object v = m.get(k); return v != null ? v.toString() : null;
    }
    private double dz(Map<String,Object> m, String k) {
        Object v = m.get(k);
        return v != null ? Double.parseDouble(v.toString()) : 0.0;
    }
}