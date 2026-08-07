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

        Optional<Produit> existant = produitRepo.findByUuid(uuid);
        boolean estNouveau = existant.isEmpty();
        Produit p = existant.orElse(Produit.builder().uuid(uuid).build());

        p.setNom(s(body, "nom"));
        p.setCategorie(s(body, "categorie"));
        p.setPrixAchat(d(body, "prixAchat") != null
                ? d(body, "prixAchat") : 0.0);
        p.setPrixVente(d(body, "prixVente") != null
                ? d(body, "prixVente") : 0.0);
        // Stock initial accepté uniquement à la création — pour un
        // produit existant, le stock ne doit être modifié QUE via les
        // ventes/mouvements verrouillés (voir SyncService.syncProduits
        // pour le détail du raisonnement).
        if (estNouveau) {
            p.setQuantiteStock(i(body, "quantiteStock") != null
                    ? i(body, "quantiteStock") : 0);
        }
        p.setStockMinimum(i(body, "stockMinimum") != null
                ? i(body, "stockMinimum") : 5);
        // photoUri : volontairement IGNORÉ ici. Chaque appareil choisit
        // sa propre photo localement pour un même produit ; le serveur
        // ne la stocke plus et ne la diffuse plus aux autres membres.
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

        // Unités de mesure : remplace intégralement la liste existante,
        // comme le fait déjà UniteProduitDB.insererPlusieurs côté local.
        // Sans ceci, créer/modifier un produit en mode multi ne
        // propageait jamais ses unités aux autres appareils — seul le
        // produit "de base" (nom/prix/stock) était transmis.
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> unitesBody =
                (List<Map<String, Object>>) body.get("unites");
        if (unitesBody != null) {
            uniteProduitRepo.deleteByProduitId(p.getId());
            // BUG CORRIGÉ : sans ce flush, Hibernate exécute par
            // défaut TOUS les INSERT en attente avant les DELETE (son
            // ordre d'action fixe, indépendant de l'ordre d'appel en
            // Java) — les nouvelles unités ci-dessous (mêmes uuid que
            // les anciennes, réutilisés tels quels par l'appareil) se
            // retrouvaient donc insérées AVANT que les anciennes
            // lignes ne soient supprimées, d'où "duplicate key value
            // violates unique constraint unites_produit_uuid_key" à
            // chaque modification d'un produit existant. Le flush
            // force la suppression à s'exécuter immédiatement.
            uniteProduitRepo.flush();
            int ordre = 0;
            for (Map<String, Object> ub : unitesBody) {
                UniteProduit u = UniteProduit.builder()
                        .uuid(s(ub, "uuid") != null
                                ? s(ub, "uuid") : UUID.randomUUID().toString())
                        .produit(p)
                        .nom(s(ub, "nom"))
                        .facteur(d(ub, "facteur") != null ? d(ub, "facteur") : 1.0)
                        .prixAchat(d(ub, "prixAchat") != null ? d(ub, "prixAchat") : 0.0)
                        .prixVente(d(ub, "prixVente") != null ? d(ub, "prixVente") : 0.0)
                        .estBase(Boolean.TRUE.equals(ub.get("estBase")))
                        .ordre(ordre++)
                        .build();
                uniteProduitRepo.save(u);
            }
        }

        return buildDto(p, true);
    }

    @Transactional(readOnly = true)
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
        // photoUri : jamais renvoyé — purement local à chaque appareil.
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
                // estReference n'est plus exposée : c'est un réglage
                // d'affichage propre à chaque membre/appareil, jamais
                // partagé entre eux via le serveur.
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