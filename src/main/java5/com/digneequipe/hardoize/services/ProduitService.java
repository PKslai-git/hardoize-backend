package com.digneequipe.hardoize.services;

import com.digneequipe.hardoize.models.*;
import com.digneequipe.hardoize.repositories.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
public class ProduitService {

    private final ProduitRepository      produitRepo;
    private final GroupeRepository       groupeRepo;
    private final FournisseurRepository  fournisseurRepo;
    private final UtilisateurRepository  utilisateurRepo;
    private final UniteProduitRepository uniteProduitRepo;

    @Transactional
    public Map<String, Object> creerOuMettreAJour(
            Map<String, Object> body, String telephone) {

        String uuid = s(body, "uuid");
        if (uuid == null)
            throw new RuntimeException("UUID obligatoire");

        Produit p = produitRepo.findByUuid(uuid)
                .orElse(Produit.builder().uuid(uuid).build());

        p.setNom(s(body, "nom"));
        p.setCategorie(s(body, "categorie"));
        p.setPrixAchat(d(body, "prixAchat") != null
                ? d(body, "prixAchat") : 0.0);
        p.setPrixVente(d(body, "prixVente") != null
                ? d(body, "prixVente") : 0.0);
        p.setQuantiteStock(i(body, "quantiteStock") != null
                ? i(body, "quantiteStock") : 0);
        p.setStockMinimum(i(body, "stockMinimum") != null
                ? i(body, "stockMinimum") : 5);
        p.setPhotoUri(s(body, "photoUri"));
        p.setEstActif(true);

        // FK Groupe
        String gUuid = s(body, "groupeUuid");
        if (gUuid != null)
            groupeRepo.findByUuid(gUuid).ifPresent(p::setGroupe);

        // FK Fournisseur
        String fUuid = s(body, "fournisseurUuid");
        if (fUuid != null)
            fournisseurRepo.findByUuid(fUuid).ifPresent(p::setFournisseur);

        // Utilisateur
        utilisateurRepo.findByTelephone(telephone)
                .ifPresent(p::setUtilisateur);

        p = produitRepo.save(p);
        return buildDto(p, true);
    }

    public List<Map<String, Object>> getByGroupe(
            Long groupeId, boolean avecUnites) {

        List<Produit> produits =
                produitRepo.findByGroupeIdAndEstActif(groupeId, true);

        List<Map<String, Object>> result = new ArrayList<>();
        for (Produit p : produits) {
            result.add(buildDto(p, avecUnites));
        }
        return result;
    }

    @Transactional
    public void decrementerStock(Long produitId, int qte) {
        produitRepo.decrementerStock(produitId, qte);
    }

    @Transactional
    public void incrementerStock(Long produitId, int qte) {
        produitRepo.incrementerStock(produitId, qte);
    }

    private Map<String, Object> buildDto(Produit p, boolean avecUnites) {
        Map<String, Object> dto = new HashMap<>();
        dto.put("id",            p.getId());
        dto.put("uuid",          p.getUuid());
        dto.put("nom",           p.getNom());
        dto.put("categorie",     p.getCategorie());
        dto.put("prixAchat",     p.getPrixAchat());
        dto.put("prixVente",     p.getPrixVente());
        dto.put("quantiteStock", p.getQuantiteStock());
        dto.put("stockMinimum",  p.getStockMinimum());
        dto.put("photoUri",      p.getPhotoUri());
        dto.put("createdAt",     p.getCreatedAt());
        dto.put("groupeUuid",    p.getGroupe() != null
                ? p.getGroupe().getUuid() : null);
        dto.put("fournisseurUuid", p.getFournisseur() != null
                ? p.getFournisseur().getUuid() : null);

        if (avecUnites) {
            List<UniteProduit> unites =
                    uniteProduitRepo.findByProduitIdOrderByOrdreAsc(p.getId());
            List<Map<String, Object>> unitesDto = new ArrayList<>();
            for (UniteProduit u : unites) {
                Map<String, Object> uDto = new HashMap<>();
                uDto.put("uuid",         u.getUuid());
                uDto.put("nom",          u.getNom());
                uDto.put("facteur",      u.getFacteur());
                uDto.put("prixAchat",    u.getPrixAchat());
                uDto.put("prixVente",    u.getPrixVente());
                uDto.put("estBase",      u.getEstBase());
                uDto.put("estReference", u.getEstReference());
                uDto.put("ordre",        u.getOrdre());
                unitesDto.add(uDto);
            }
            dto.put("unites", unitesDto);
        }

        return dto;
    }

    private String s(Map<String,Object> m, String k) {
        Object v = m.get(k); return v != null ? v.toString() : null;
    }
    private Double d(Map<String,Object> m, String k) {
        Object v = m.get(k);
        return v != null ? Double.parseDouble(v.toString()) : null;
    }
    private Integer i(Map<String,Object> m, String k) {
        Object v = m.get(k);
        return v != null ? Integer.parseInt(v.toString()) : null;
    }
}