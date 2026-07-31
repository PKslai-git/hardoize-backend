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

        Utilisateur auteurSolo = utilisateurRepo.findByTelephone(telephone).orElse(null);
        if (auteurSolo != null) {
            m.setUtilisateur(auteurSolo);
            m.setNomUtilisateur(auteurSolo.getNom());
        }

        m = mouvementRepo.save(m);
        return buildDto(m);
    }

    // ── Mode Multi : mouvement avec ajustement atomique du stock ──
    // Contrairement à creerOuMaj (mode solo, qui ne fait que journaliser
    // le mouvement — l'ajustement du stock est alors calculé et déjà
    // appliqué localement sur l'appareil), cette méthode est la seule
    // source de vérité en mode multi : c'est ELLE qui modifie
    // quantiteStock sur le serveur, de façon atomique, et renvoie la
    // nouvelle valeur pour que le client l'applique en écrasement local.
    @Transactional
    public Map<String, Object> enregistrerMulti(
            Map<String, Object> body, String telephone) {

        String uuid = s(body, "uuid");
        if (uuid == null) throw new RuntimeException("UUID obligatoire");

        // Idempotence : une réémission depuis la file d'attente
        // hors-ligne ne doit jamais appliquer le mouvement deux fois.
        if (mouvementRepo.existsByUuid(uuid)) {
            MouvementStock existant = mouvementRepo.findByUuid(uuid).orElse(null);
            if (existant != null) {
                Map<String, Object> dto = buildDto(existant);
                if (existant.getProduit() != null) {
                    Produit p = produitRepo.findById(existant.getProduit().getId())
                            .orElse(existant.getProduit());
                    dto.put("quantiteStock", p.getQuantiteStock());
                }
                dto.put("dejaTraitee", true);
                return dto;
            }
        }

        String pUuid = s(body, "produitUuid");
        if (pUuid == null) throw new RuntimeException("produitUuid manquant");

        // ── Exclusion mutuelle à attente active ─────────────────────
        // Voir VenteService.enregistrerMulti pour le détail : verrouille
        // la ligne du produit pour toute la transaction, garantit un
        // traitement strictement séquentiel des opérations concurrentes
        // sur le même produit, et évite la lecture obsolète que
        // provoquait l'ancienne combinaison UPDATE atomique + relecture.
        Produit produit = produitRepo.findByUuidPourMiseAJour(pUuid)
                .orElseThrow(() -> new RuntimeException("Produit introuvable"));

        String type = s(body, "type") != null ? s(body, "type") : "entree";
        int quantiteBase = i(body, "quantite") != null ? i(body, "quantite") : 0;
        boolean estEntree = "entree".equals(type) || "retour".equals(type);

        if (estEntree) {
            produit.setQuantiteStock(produit.getQuantiteStock() + quantiteBase);
        } else {
            if (produit.getQuantiteStock() < quantiteBase) {
                throw new RuntimeException(
                        "Stock insuffisant pour " + produit.getNom() +
                                " (modifié entre-temps par un autre membre)");
            }
            produit.setQuantiteStock(produit.getQuantiteStock() - quantiteBase);
        }
        produitRepo.save(produit);

        MouvementStock m = MouvementStock.builder().uuid(uuid).build();
        m.setNomProduit(produit.getNom());
        m.setType(type);
        m.setMotif(s(body, "motif"));
        m.setQuantite(quantiteBase);
        m.setNomUnite(s(body, "nomUnite") != null ? s(body, "nomUnite") : "pcs");
        m.setQteUnite(i(body, "qteUnite") != null ? i(body, "qteUnite") : quantiteBase);
        m.setPrixUnitaire(d(body, "prixUnitaire") != null ? d(body, "prixUnitaire") : 0.0);
        m.setMontantTotal(d(body, "montantTotal") != null ? d(body, "montantTotal") : 0.0);
        m.setMontantPaye(d(body, "montantPaye") != null ? d(body, "montantPaye") : 0.0);
        m.setModePaiement(s(body, "modePaiement"));
        m.setProduit(produit);

        String fUuid = s(body, "fournisseurUuid");
        if (fUuid != null)
            fournisseurRepo.findByUuid(fUuid).ifPresent(m::setFournisseur);

        String gUuid = s(body, "groupeUuid");
        if (gUuid != null)
            groupeRepo.findByUuid(gUuid).ifPresent(m::setGroupe);

        Utilisateur auteur = utilisateurRepo.findByTelephone(telephone).orElse(null);
        if (auteur != null) {
            m.setUtilisateur(auteur);
            m.setNomUtilisateur(auteur.getNom());
        }

        m = mouvementRepo.save(m);

        Map<String, Object> dto = buildDto(m);
        dto.put("quantiteStock", produit.getQuantiteStock());
        return dto;
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
        dto.put("nomUtilisateur", m.getNomUtilisateur());
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