package com.digneequipe.hardoize.services;

import com.digneequipe.hardoize.models.*;
import com.digneequipe.hardoize.repositories.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
public class FournisseurService {

    private final FournisseurRepository      fournisseurRepo;
    private final DetteFournisseurRepository detteFourn;
    private final GroupeRepository           groupeRepo;

    @Transactional
    public Map<String, Object> creerOuMettreAJour(
            Map<String, Object> body) {

        String uuid = s(body, "uuid");
        if (uuid == null) throw new RuntimeException("UUID obligatoire");

        Fournisseur f = fournisseurRepo.findByUuid(uuid)
                .orElse(Fournisseur.builder().uuid(uuid).build());

        f.setNom(s(body, "nom"));
        f.setTelephone(s(body, "telephone"));
        f.setEmail(s(body, "email"));
        f.setAdresse(s(body, "adresse"));
        f.setPhotoUri(s(body, "photoUri"));
        f.setEstActif(true);

        String gUuid = s(body, "groupeUuid");
        if (gUuid != null)
            groupeRepo.findByUuid(gUuid).ifPresent(f::setGroupe);

        f = fournisseurRepo.save(f);
        return buildDto(f);
    }

    public List<Map<String, Object>> getByGroupe(Long groupeId) {
        List<Fournisseur> fourns =
                fournisseurRepo.findByGroupeIdAndEstActif(groupeId, true);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Fournisseur f : fourns) result.add(buildDto(f));
        return result;
    }

    @Transactional
    public Map<String, Object> creerOuMajDette(
            Map<String, Object> body) {

        String uuid = s(body, "uuid");
        if (uuid == null) throw new RuntimeException("UUID obligatoire");

        DetteFournisseur df = detteFourn.findByUuid(uuid)
                .orElse(DetteFournisseur.builder().uuid(uuid).build());

        df.setNomFournisseur(s(body, "nomFournisseur"));
        df.setMontantTotal(d(body, "montantTotal") != null
                ? d(body, "montantTotal") : 0.0);
        df.setMontantRembourse(d(body, "montantRembourse") != null
                ? d(body, "montantRembourse") : 0.0);
        df.setMontantRestant(d(body, "montantRestant") != null
                ? d(body, "montantRestant")
                : df.getMontantTotal() - df.getMontantRembourse());
        df.setMotif(s(body, "motif"));
        df.setStatut(s(body, "statut") != null
                ? s(body, "statut") : "active");
        df.setPaiementsJson(s(body, "paiementsJson"));

        String fUuid = s(body, "fournisseurUuid");
        if (fUuid != null)
            fournisseurRepo.findByUuid(fUuid)
                    .ifPresent(df::setFournisseur);

        String gUuid = s(body, "groupeUuid");
        if (gUuid != null)
            groupeRepo.findByUuid(gUuid).ifPresent(df::setGroupe);

        df = detteFourn.save(df);
        return buildDetteDto(df);
    }

    public List<Map<String, Object>> getDettesByGroupe(Long groupeId) {
        List<DetteFournisseur> dettes =
                detteFourn.findByGroupeId(groupeId);
        List<Map<String, Object>> result = new ArrayList<>();
        for (DetteFournisseur df : dettes) result.add(buildDetteDto(df));
        return result;
    }

    private Map<String, Object> buildDto(Fournisseur f) {
        Map<String, Object> dto = new HashMap<>();
        dto.put("id",         f.getId());
        dto.put("uuid",       f.getUuid());
        dto.put("nom",        f.getNom());
        dto.put("telephone",  f.getTelephone());
        dto.put("email",      f.getEmail());
        dto.put("adresse",    f.getAdresse());
        dto.put("groupeUuid", f.getGroupe() != null
                ? f.getGroupe().getUuid() : null);
        dto.put("createdAt",  f.getCreatedAt());
        return dto;
    }

    private Map<String, Object> buildDetteDto(DetteFournisseur df) {
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