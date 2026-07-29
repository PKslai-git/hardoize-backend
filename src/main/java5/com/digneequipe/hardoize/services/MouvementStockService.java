package com.digneequipe.hardoize.services;

import com.digneequipe.hardoize.models.*;
import com.digneequipe.hardoize.repositories.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
public class MouvementStockService {

    private final MouvementStockRepository mouvementRepo;
    private final ProduitRepository        produitRepo;
    private final FournisseurRepository    fournisseurRepo;
    private final GroupeRepository         groupeRepo;
    private final UtilisateurRepository    utilisateurRepo;

    @Transactional
    public Map<String, Object> creerOuMaj(
            Map<String, Object> body, String telephone) {

        String uuid = s(body, "uuid");
        if (uuid == null) throw new RuntimeException("UUID obligatoire");

        if (mouvementRepo.existsByUuid(uuid)) {
            return mouvementRepo.findByUuid(uuid)
                    .map(this::buildDto)
                    .orElse(Map.of("uuid", uuid));
        }

        MouvementStock m = MouvementStock.builder()
                .uuid(uuid)
                .build();

        m.setNomProduit(s(body, "nomProduit"));
        m.setType(s(body, "type") != null
                ? s(body, "type") : "entree");
        m.setMotif(s(body, "motif"));
        m.setQuantite(i(body, "quantite") != null
                ? i(body, "quantite") : 0);
        m.setNomUnite(s(body, "nomUnite") != null
                ? s(body, "nomUnite") : "pcs");
        m.setQteUnite(i(body, "qteUnite") != null
                ? i(body, "qteUnite") : m.getQuantite());
        m.setPrixUnitaire(d(body, "prixUnitaire") != null
                ? d(body, "prixUnitaire") : 0.0);
        m.setMontantTotal(d(body, "montantTotal") != null
                ? d(body, "montantTotal") : 0.0);
        m.setMontantPaye(d(body, "montantPaye") != null
                ? d(body, "montantPaye") : 0.0);
        m.setModePaiement(s(body, "modePaiement"));

        String pUuid = s(body, "produitUuid");
        if (pUuid != null)
            produitRepo.findByUuid(pUuid).ifPresent(m::setProduit);

        String fUuid = s(body, "fournisseurUuid");
        if (fUuid != null)
            fournisseurRepo.findByUuid(fUuid)
                    .ifPresent(m::setFournisseur);

        String gUuid = s(body, "groupeUuid");
        if (gUuid != null)
            groupeRepo.findByUuid(gUuid).ifPresent(m::setGroupe);

        utilisateurRepo.findByTelephone(telephone)
                .ifPresent(m::setUtilisateur);

        m = mouvementRepo.save(m);
        return buildDto(m);
    }

    public List<Map<String, Object>> getByGroupe(Long groupeId) {
        List<MouvementStock> mvts =
                mouvementRepo.findByGroupeIdOrderByCreatedAtDesc(groupeId);
        List<Map<String, Object>> result = new ArrayList<>();
        for (MouvementStock m : mvts) result.add(buildDto(m));
        return result;
    }

    private Map<String, Object> buildDto(MouvementStock m) {
        Map<String, Object> dto = new HashMap<>();
        dto.put("id",          m.getId());
        dto.put("uuid",        m.getUuid());
        dto.put("nomProduit",  m.getNomProduit());
        dto.put("type",        m.getType());
        dto.put("motif",       m.getMotif());
        dto.put("quantite",    m.getQuantite());
        dto.put("nomUnite",    m.getNomUnite());
        dto.put("qteUnite",    m.getQteUnite());
        dto.put("prixUnitaire",m.getPrixUnitaire());
        dto.put("montantTotal",m.getMontantTotal());
        dto.put("produitUuid", m.getProduit() != null
                ? m.getProduit().getUuid() : null);
        dto.put("groupeUuid",  m.getGroupe() != null
                ? m.getGroupe().getUuid() : null);
        dto.put("createdAt",   m.getCreatedAt());
        return dto;
    }

    private String  s(Map<String,Object> m, String k) {
        Object v = m.get(k); return v != null ? v.toString() : null;
    }
    private Double  d(Map<String,Object> m, String k) {
        Object v = m.get(k);
        return v != null ? Double.parseDouble(v.toString()) : null;
    }
    private Integer i(Map<String,Object> m, String k) {
        Object v = m.get(k);
        return v != null ? Integer.parseInt(v.toString()) : null;
    }
}