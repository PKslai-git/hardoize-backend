package com.digneequipe.hardoize.services;

import com.digneequipe.hardoize.models.*;
import com.digneequipe.hardoize.repositories.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
public class VenteService {

    private final VenteRepository       venteRepo;
    private final LigneVenteRepository  ligneVenteRepo;
    private final DetteRepository       detteRepo;
    private final ProduitRepository     produitRepo;
    private final ClientRepository      clientRepo;
    private final GroupeRepository      groupeRepo;
    private final UtilisateurRepository utilisateurRepo;

    // ── Mode Solo : stocker sans vérification ─────────────────
    @Transactional
    public Map<String, Object> creerOuMajVente(
            Map<String, Object> body, String telephone) {

        String uuid = s(body, "uuid");
        if (uuid == null) throw new RuntimeException("UUID obligatoire");

        // Idempotent
        if (venteRepo.existsByUuid(uuid)) {
            return venteRepo.findByUuid(uuid)
                    .map(this::buildDto)
                    .orElse(Map.of("uuid", uuid));
        }

        Vente v = Vente.builder().uuid(uuid).build();
        v.setMontantTotal(d(body, "montantTotal") != null
                ? d(body, "montantTotal") : 0.0);
        v.setBeneficeNet(d(body, "beneficeNet") != null
                ? d(body, "beneficeNet") : 0.0);
        v.setTypePaiement(s(body, "typePaiement") != null
                ? s(body, "typePaiement") : "especes");

        String gUuid = s(body, "groupeUuid");
        if (gUuid != null)
            groupeRepo.findByUuid(gUuid).ifPresent(v::setGroupe);

        String cUuid = s(body, "clientUuid");
        if (cUuid != null)
            clientRepo.findByUuid(cUuid).ifPresent(v::setClient);

        utilisateurRepo.findByTelephone(telephone)
                .ifPresent(v::setUtilisateur);

        v = venteRepo.save(v);
        return buildDto(v);
    }

    // ── Mode Multi : vente avec vérification stock ─────────────
    @Transactional
    public Map<String, Object> enregistrerMulti(
            Map<String, Object> body, String telephone) {

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> lignesBody =
                (List<Map<String, Object>>) body.get("lignes");

        if (lignesBody == null || lignesBody.isEmpty())
            throw new RuntimeException("Panier vide");

        Utilisateur user = utilisateurRepo
                .findByTelephone(telephone)
                .orElseThrow(() -> new RuntimeException("Utilisateur introuvable"));

        Groupe groupe = null;
        String gUuid = s(body, "groupeUuid");
        if (gUuid != null)
            groupe = groupeRepo.findByUuid(gUuid).orElse(null);

        Client client = null;
        String cUuid = s(body, "clientUuid");
        if (cUuid != null)
            client = clientRepo.findByUuid(cUuid).orElse(null);

        double montantTotal = 0;
        double beneficeNet  = 0;

        // Vérifier le stock pour chaque ligne
        for (Map<String, Object> ligne : lignesBody) {
            String pUuid = s(ligne, "produitUuid");
            if (pUuid == null) continue;

            Produit produit = produitRepo.findByUuid(pUuid)
                    .orElseThrow(() ->
                            new RuntimeException("Produit introuvable: " + pUuid));

            int qteBase = i(ligne, "qteBase") != null
                    ? i(ligne, "qteBase") : i(ligne, "quantite") != null
                    ? i(ligne, "quantite") : 1;

            if (produit.getQuantiteStock() < qteBase) {
                throw new RuntimeException(
                        "Stock insuffisant pour " + produit.getNom() +
                                ". Disponible: " + produit.getQuantiteStock()
                );
            }

            double prix = d(ligne, "prixUnitaire") != null
                    ? d(ligne, "prixUnitaire") : produit.getPrixVente();
            double prixAchat = d(ligne, "prixAchat") != null
                    ? d(ligne, "prixAchat") : produit.getPrixAchat();
            double sousTotal = prix * (
                    i(ligne, "quantite") != null
                            ? i(ligne, "quantite") : 1);

            montantTotal += sousTotal;
            beneficeNet  += (prix - prixAchat) * qteBase;
        }

        // Créer la vente
        Vente vente = Vente.builder()
                .uuid(UUID.randomUUID().toString())
                .montantTotal(montantTotal)
                .beneficeNet(beneficeNet)
                .typePaiement(s(body, "typePaiement") != null
                        ? s(body, "typePaiement") : "especes")
                .client(client)
                .utilisateur(user)
                .groupe(groupe)
                .build();
        vente = venteRepo.save(vente);

        // Créer les lignes + décrémenter stock
        List<Map<String, Object>> lignesDto = new ArrayList<>();
        for (Map<String, Object> ligneBody : lignesBody) {
            String pUuid = s(ligneBody, "produitUuid");
            if (pUuid == null) continue;

            Produit produit = produitRepo.findByUuid(pUuid).orElse(null);
            if (produit == null) continue;

            int qteAffichee = i(ligneBody, "quantite") != null
                    ? i(ligneBody, "quantite") : 1;
            int qteBase     = i(ligneBody, "qteBase") != null
                    ? i(ligneBody, "qteBase") : qteAffichee;
            double prix     = d(ligneBody, "prixUnitaire") != null
                    ? d(ligneBody, "prixUnitaire") : produit.getPrixVente();
            double prixAchat = d(ligneBody, "prixAchat") != null
                    ? d(ligneBody, "prixAchat") : produit.getPrixAchat();
            double sousTotal = prix * qteAffichee;
            double marge     = (prix - prixAchat) * qteAffichee;

            LigneVente ligne = LigneVente.builder()
                    .uuid(UUID.randomUUID().toString())
                    .vente(vente)
                    .produit(produit)
                    .nomProduit(produit.getNom())
                    .quantite(qteAffichee)
                    .uniteNom(s(ligneBody, "uniteNom") != null
                            ? s(ligneBody, "uniteNom") : "pcs")
                    .uniteFacteur(d(ligneBody, "uniteFacteur") != null
                            ? d(ligneBody, "uniteFacteur") : 1.0)
                    .prixAchat(prixAchat)
                    .prixUnitaire(prix)
                    .sousTotal(sousTotal)
                    .marge(marge)
                    .build();
            ligneVenteRepo.save(ligne);

            // Décrémenter stock
            produitRepo.decrementerStock(produit.getId(), qteBase);

            Map<String, Object> lDto = new HashMap<>();
            lDto.put("uuid",        ligne.getUuid());
            lDto.put("nomProduit",  ligne.getNomProduit());
            lDto.put("quantite",    ligne.getQuantite());
            lDto.put("uniteNom",    ligne.getUniteNom());
            lDto.put("prixUnitaire",ligne.getPrixUnitaire());
            lDto.put("sousTotal",   ligne.getSousTotal());
            lignesDto.add(lDto);
        }

        // Dette si crédit
        if ("credit".equals(s(body, "typePaiement"))
                && client != null) {
            String dateRembStr = s(body, "dateRemboursement");
            LocalDateTime dateRemb = dateRembStr != null
                    ? LocalDate.parse(dateRembStr,
                            DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                    .atTime(23, 59, 59)
                    : LocalDateTime.now().plusDays(30);

            Dette dette = Dette.builder()
                    .uuid(UUID.randomUUID().toString())
                    .vente(vente)
                    .client(client)
                    .montantTotal(montantTotal)
                    .montantRembourse(0.0)
                    .montantRestant(montantTotal)
                    .dateRemboursement(dateRemb)
                    .utilisateur(user)
                    .groupe(groupe)
                    .build();
            detteRepo.save(dette);
        }

        Map<String, Object> dto = buildDto(vente);
        dto.put("lignes", lignesDto);
        return dto;
    }

    public List<Map<String, Object>> getByGroupe(
            Long groupeId, boolean avecLignes) {
        List<Vente> ventes =
                venteRepo.findByGroupeIdOrderByCreatedAtDesc(groupeId);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Vente v : ventes) {
            Map<String, Object> dto = buildDto(v);
            if (avecLignes) {
                List<LigneVente> lignes =
                        ligneVenteRepo.findByVenteId(v.getId());
                List<Map<String, Object>> lignesDto = new ArrayList<>();
                for (LigneVente l : lignes) {
                    Map<String, Object> lDto = new HashMap<>();
                    lDto.put("uuid",         l.getUuid());
                    lDto.put("produitUuid",  l.getProduit() != null
                            ? l.getProduit().getUuid() : null);
                    lDto.put("nomProduit",   l.getNomProduit());
                    lDto.put("quantite",     l.getQuantite());
                    lDto.put("uniteNom",     l.getUniteNom());
                    lDto.put("uniteFacteur", l.getUniteFacteur());
                    lDto.put("prixAchat",    l.getPrixAchat());
                    lDto.put("prixUnitaire", l.getPrixUnitaire());
                    lDto.put("sousTotal",    l.getSousTotal());
                    lDto.put("marge",        l.getMarge());
                    lignesDto.add(lDto);
                }
                dto.put("lignes", lignesDto);
            }
            result.add(dto);
        }
        return result;
    }

    private Map<String, Object> buildDto(Vente v) {
        Map<String, Object> dto = new HashMap<>();
        dto.put("id",          v.getId());
        dto.put("uuid",        v.getUuid());
        dto.put("montantTotal",v.getMontantTotal());
        dto.put("beneficeNet", v.getBeneficeNet());
        dto.put("typePaiement",v.getTypePaiement());
        dto.put("clientUuid",  v.getClient() != null
                ? v.getClient().getUuid() : null);
        dto.put("groupeUuid",  v.getGroupe() != null
                ? v.getGroupe().getUuid() : null);
        dto.put("vendeurNom",  v.getUtilisateur() != null
                ? v.getUtilisateur().getNom() : null);
        dto.put("createdAt",   v.getCreatedAt());
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