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

    private final ProduitRepository        produitRepo;
    private final GroupeRepository         groupeRepo;
    private final FournisseurRepository    fournisseurRepo;
    private final UtilisateurRepository    utilisateurRepo;
    private final UniteProduitRepository   uniteProduitRepo;
    private final MouvementStockRepository mouvementStockRepo;

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

    // ── Mode Multi : entrée/sortie de stock traitée par le backend ────
    // Même principe que VenteService.enregistrerMulti : le serveur
    // applique lui-même le mouvement sur le stock Supabase (jamais le
    // frontend), de façon atomique et conditionnée en SQL pour rester
    // correct si deux membres agissent sur le même produit au même
    // moment, puis renvoie le stock réellement à jour pour que
    // l'appareil appelant écrase sa valeur locale (jamais fiable tant
    // qu'elle n'a pas été confirmée par le serveur).
    @Transactional
    public Map<String, Object> mouvementStockMulti(
            Map<String, Object> body, String telephone) {

        String pUuid = s(body, "produitUuid");
        if (pUuid == null) throw new RuntimeException("produitUuid manquant");

        Produit produit = produitRepo.findByUuid(pUuid)
                .orElseThrow(() -> new RuntimeException("Produit introuvable"));

        String type = s(body, "type") != null ? s(body, "type") : "entree";
        boolean estEntree = "entree".equals(type);

        int qteBase = i(body, "qteBase") != null
                ? i(body, "qteBase")
                : (i(body, "quantite") != null ? i(body, "quantite") : 0);
        if (qteBase <= 0) throw new RuntimeException("Quantité invalide");

        if (estEntree) {
            produitRepo.incrementerStock(produit.getId(), qteBase);
        } else {
            int lignesAffectees = produitRepo.decrementerStock(produit.getId(), qteBase);
            if (lignesAffectees == 0) {
                Produit actuel = produitRepo.findByUuid(pUuid).orElse(produit);
                throw new RuntimeException(
                        "Stock insuffisant pour " + actuel.getNom() +
                        ". Disponible: " + actuel.getQuantiteStock());
            }
        }

        // Journal du mouvement — même table que le flux solo, mais créée
        // ici dans la même transaction que le changement de stock (le
        // flux solo, lui, ne fait qu'enregistrer un mouvement déjà
        // appliqué localement ; ici les deux se font ensemble côté
        // serveur, seule source de vérité).
        MouvementStock m = MouvementStock.builder()
                .uuid(s(body, "mouvementUuid") != null
                        ? s(body, "mouvementUuid")
                        : java.util.UUID.randomUUID().toString())
                .produit(produit)
                .nomProduit(produit.getNom())
                .type(estEntree ? "entree" : "sortie")
                .motif(s(body, "motif"))
                .quantite(qteBase)
                .nomUnite(s(body, "uniteNom") != null ? s(body, "uniteNom") : "pcs")
                .qteUnite(i(body, "quantite") != null ? i(body, "quantite") : qteBase)
                .prixUnitaire(d(body, "prixUnitaire") != null ? d(body, "prixUnitaire") : 0.0)
                .montantTotal(d(body, "montantTotal") != null ? d(body, "montantTotal") : 0.0)
                .montantPaye(d(body, "montantPaye") != null ? d(body, "montantPaye") : 0.0)
                .modePaiement(s(body, "modePaiement"))
                .build();

        String fUuid = s(body, "fournisseurUuid");
        if (fUuid != null)
            fournisseurRepo.findByUuid(fUuid).ifPresent(m::setFournisseur);

        String gUuid = s(body, "groupeUuid");
        if (gUuid != null)
            groupeRepo.findByUuid(gUuid).ifPresent(m::setGroupe);

        utilisateurRepo.findByTelephone(telephone).ifPresent(m::setUtilisateur);

        m = mouvementStockRepo.save(m);

        Produit produitMisAJour = produitRepo.findByUuid(pUuid).orElse(produit);

        Map<String, Object> dto = new HashMap<>();
        dto.put("produitUuid",   produitMisAJour.getUuid());
        dto.put("quantiteStock", produitMisAJour.getQuantiteStock());
        dto.put("mouvementUuid", m.getUuid());
        return dto;
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