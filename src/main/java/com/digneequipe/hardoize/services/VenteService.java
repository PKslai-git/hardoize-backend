package com.digneequipe.hardoize.services;

import com.digneequipe.hardoize.models.*;
import com.digneequipe.hardoize.repositories.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
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
    private final HistoriqueService     historiqueService;
    private final MouvementStockRepository mouvementStockRepo;

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

        // Idempotence par uuid : si cette vente a déjà été traitée (ex.
        // la requête a réussi côté serveur mais la réponse s'est perdue
        // en route, et le client — depuis sa file d'attente hors-ligne —
        // retente la même opération), on ne la rejoue PAS : on renvoie
        // simplement le résultat déjà obtenu, sans double décrément.
        String uuidVente = s(body, "uuid");
        if (uuidVente != null && venteRepo.existsByUuid(uuidVente)) {
            Vente existante = venteRepo.findByUuid(uuidVente).orElse(null);
            if (existante != null) {
                Map<String, Object> dtoExistant = buildDto(existante);
                List<Map<String, Object>> lignesExistantes = new ArrayList<>();
                List<Map<String, Object>> stocksExistants = new ArrayList<>();
                for (LigneVente l : ligneVenteRepo.findByVenteId(existante.getId())) {
                    Map<String, Object> lDto = new HashMap<>();
                    lDto.put("uuid", l.getUuid());
                    lDto.put("produitUuid", l.getProduit() != null
                            ? l.getProduit().getUuid() : null);
                    lDto.put("nomProduit", l.getNomProduit());
                    lDto.put("quantite", l.getQuantite());
                    lDto.put("uniteNom", l.getUniteNom());
                    lDto.put("uniteFacteur", l.getUniteFacteur());
                    lDto.put("prixAchat", l.getPrixAchat());
                    lDto.put("prixUnitaire", l.getPrixUnitaire());
                    lDto.put("sousTotal", l.getSousTotal());
                    lDto.put("marge", l.getMarge());
                    lignesExistantes.add(lDto);
                    if (l.getProduit() != null) {
                        Map<String, Object> sDto = new HashMap<>();
                        sDto.put("produitUuid", l.getProduit().getUuid());
                        sDto.put("quantiteStock", l.getProduit().getQuantiteStock());
                        stocksExistants.add(sDto);
                    }
                }
                dtoExistant.put("lignes", lignesExistantes);
                dtoExistant.put("stocksMisAJour", stocksExistants);
                dtoExistant.put("dejaTraitee", true);
                return dtoExistant;
            }
        }

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

        // Trier les lignes par produitUuid : garantit que toutes les
        // transactions concurrentes verrouillent les produits dans le
        // MÊME ordre, quel que soit l'ordre du panier de chaque membre.
        // Sans ça, une vente A→B et une vente B→A pourraient chacune
        // tenir un verrou que l'autre attend indéfiniment (interblocage).
        List<Map<String, Object>> lignesTriees = new ArrayList<>(lignesBody);
        lignesTriees.sort(Comparator.comparing(l -> String.valueOf(l.get("produitUuid"))));

        // Créer la vente (les lignes/décréments sont ajoutés ensuite,
        // une fois chaque produit verrouillé et vérifié).
        Vente vente = Vente.builder()
                .uuid(uuidVente != null ? uuidVente : UUID.randomUUID().toString())
                .montantTotal(0.0) // colonne NOT NULL — vraie valeur posée après la boucle
                .typePaiement(s(body, "typePaiement") != null
                        ? s(body, "typePaiement") : "especes")
                .client(client)
                .utilisateur(user)
                .groupe(groupe)
                .build();
        vente = venteRepo.save(vente);

        List<Map<String, Object>> lignesDto = new ArrayList<>();
        List<Map<String, Object>> stocksMisAJour = new ArrayList<>();
        List<Map<String, Object>> mouvementsStockDto = new ArrayList<>();

        for (Map<String, Object> ligneBody : lignesTriees) {
            String pUuid = s(ligneBody, "produitUuid");
            if (pUuid == null) continue;

            // ── Exclusion mutuelle à attente active ─────────────────
            // Verrouille la ligne du produit pour toute la durée de
            // cette transaction. Si un autre membre vend le même
            // produit au même instant, sa transaction reste bloquée
            // ICI jusqu'à ce que celle-ci commit — elle reprend alors
            // avec le stock réellement à jour, jamais une valeur mise
            // en cache. Les deux ventes sont ainsi traitées l'une après
            // l'autre, jamais en parallèle sur le même produit.
            Produit produit = produitRepo.findByUuidPourMiseAJour(pUuid)
                    .orElseThrow(() ->
                            new RuntimeException("Produit introuvable: " + pUuid));

            // Log volontairement laissé en place (pas juste temporaire) :
            // permet de VÉRIFIER en production, dans les logs serveur,
            // que deux ventes concurrentes sur le même produit sont bien
            // traitées l'une après l'autre (thread différent, mais
            // jamais le même stockAvant deux fois de suite pour le même
            // produit avant qu'un décrément n'ait été commité).
            log.info("Vente {} — verrou obtenu sur produit {} (stockAvant={}) [thread={}]",
                    vente.getUuid(), pUuid, produit.getQuantiteStock(),
                    Thread.currentThread().getName());

            int qteAffichee = i(ligneBody, "quantite") != null
                    ? i(ligneBody, "quantite") : 1;
            int qteBase     = i(ligneBody, "qteBase") != null
                    ? i(ligneBody, "qteBase") : qteAffichee;

            if (produit.getQuantiteStock() < qteBase) {
                throw new RuntimeException(
                        "Stock insuffisant pour " + produit.getNom() +
                                ". Disponible: " + produit.getQuantiteStock());
            }

            double prix = d(ligneBody, "prixUnitaire") != null
                    ? d(ligneBody, "prixUnitaire") : produit.getPrixVente();
            double prixAchat = d(ligneBody, "prixAchat") != null
                    ? d(ligneBody, "prixAchat") : produit.getPrixAchat();
            double sousTotal = prix * qteAffichee;
            double marge     = (prix - prixAchat) * qteAffichee;

            montantTotal += sousTotal;
            beneficeNet  += (prix - prixAchat) * qteBase;

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

            // Modification directe de l'entité verrouillée — le
            // dirty-checking JPA l'écrira au commit avec la valeur
            // exacte, plus besoin de relire ensuite (donc plus de
            // risque de lecture obsolète en cache).
            produit.setQuantiteStock(produit.getQuantiteStock() - qteBase);
            produitRepo.save(produit);

            log.info("Vente {} — produit {} décrémenté de {} (stockApres={}) [thread={}]",
                    vente.getUuid(), pUuid, qteBase, produit.getQuantiteStock(),
                    Thread.currentThread().getName());

            Map<String, Object> stockDto = new HashMap<>();
            stockDto.put("produitUuid",   produit.getUuid());
            stockDto.put("quantiteStock", produit.getQuantiteStock());
            stocksMisAJour.add(stockDto);

            // BUG CORRIGÉ : une vente ne créait jamais de ligne dans
            // mouvements_stock — l'onglet "Mouvements" de l'écran Stock
            // n'affichait donc jamais les sorties liées aux ventes
            // (seuls achat/perte/retour/inventaire, créés depuis cet
            // écran, y apparaissaient). Même table, motif "vente".
            MouvementStock mouvement = MouvementStock.builder()
                    .uuid(UUID.randomUUID().toString())
                    .produit(produit)
                    .nomProduit(produit.getNom())
                    .type("sortie")
                    .motif("vente")
                    .quantite(qteBase)
                    .NomUnite(s(ligneBody, "uniteNom") != null
                            ? s(ligneBody, "uniteNom") : "pcs")
                    .QteUnite(qteAffichee)
                    .prixUnitaire(prix)
                    .montantTotal(sousTotal)
                    .utilisateur(user)
                    .nomUtilisateur(user.getNom())
                    .groupe(groupe)
                    .build();
            mouvementStockRepo.save(mouvement);

            Map<String, Object> mDto = new HashMap<>();
            mDto.put("uuid",          mouvement.getUuid());
            mDto.put("produitUuid",   produit.getUuid());
            mDto.put("nomProduit",    produit.getNom());
            mDto.put("type",          "sortie");
            mDto.put("motif",         "vente");
            mDto.put("quantite",      qteBase);
            mDto.put("nomUnite",      mouvement.getNomUnite());
            mDto.put("qteUnite",      qteAffichee);
            mDto.put("prixUnitaire",  prix);
            mDto.put("montantTotal",  sousTotal);
            mDto.put("nomUtilisateur", user.getNom());
            mDto.put("createdAt", mouvement.getCreatedAt() != null
                    ? mouvement.getCreatedAt()
                            .atZone(ZoneOffset.UTC).toInstant().toEpochMilli()
                    : System.currentTimeMillis());
            mouvementsStockDto.add(mDto);

            Map<String, Object> lDto = new HashMap<>();
            lDto.put("uuid",        ligne.getUuid());
            lDto.put("produitUuid", produit.getUuid());
            lDto.put("nomProduit",  ligne.getNomProduit());
            lDto.put("quantite",    ligne.getQuantite());
            lDto.put("uniteNom",    ligne.getUniteNom());
            lDto.put("uniteFacteur",ligne.getUniteFacteur());
            lDto.put("prixAchat",   ligne.getPrixAchat());
            lDto.put("prixUnitaire",ligne.getPrixUnitaire());
            lDto.put("sousTotal",   ligne.getSousTotal());
            lDto.put("marge",       ligne.getMarge());
            lignesDto.add(lDto);
        }

        vente.setMontantTotal(montantTotal);
        vente.setBeneficeNet(beneficeNet);
        vente = venteRepo.save(vente);

        Dette detteCreee = null;
        // Dette si crédit
        if ("credit".equals(s(body, "typePaiement"))
                && client != null) {
            String dateRembStr = s(body, "dateRemboursement");
            // IMPORTANT : VentesScreen (mode multi) envoie un timestamp
            // ms (Date.getTime()), pas une chaîne "JJ/MM/AAAA" — avant
            // cette correction, LocalDate.parse levait une exception non
            // rattrapée sur CE format, faisant échouer toute la vente à
            // crédit. On essaie donc d'abord le timestamp ms (le cas
            // réel), puis "JJ/MM/AAAA" par compatibilité, avec repli
            // silencieux à 30 jours si rien ne correspond — jamais de
            // vente perdue à cause d'un format de date.
            LocalDateTime dateRemb;
            if (dateRembStr != null) {
                LocalDateTime parsed = null;
                try {
                    long ms = Long.parseLong(dateRembStr);
                    parsed = LocalDateTime.ofInstant(
                            Instant.ofEpochMilli(ms), ZoneOffset.UTC);
                } catch (NumberFormatException e1) {
                    try {
                        parsed = LocalDate.parse(dateRembStr,
                                DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                                .atTime(23, 59, 59);
                    } catch (Exception e2) {
                        parsed = null;
                    }
                }
                dateRemb = parsed != null
                        ? parsed
                        : LocalDateTime.now(ZoneOffset.UTC).plusDays(30);
            } else {
                dateRemb = LocalDateTime.now(ZoneOffset.UTC).plusDays(30);
            }

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
            detteCreee = dette;
        }

        // Historique ventes du jour : incrément atomique côté serveur,
        // seule source de vérité en mode multi (voir HistoriqueService.
        // incrementerVenteMulti) — inclus dans le résultat pour que
        // CET appareil ET tous les autres (via l'écho WebSocket de
        // MultiModeController) appliquent le même total à jour, sans
        // jamais recalculer localement (qui désynchroniserait dès que
        // deux vendeurs vendent le même jour).
        Map<String, Object> historiqueDto = groupe != null
                ? historiqueService.incrementerVenteMulti(
                        groupe.getId(), groupe.getUuid(),
                        montantTotal, beneficeNet, s(body, "typePaiement"))
                : null;

        Map<String, Object> dto = buildDto(vente);
        dto.put("lignes", lignesDto);
        dto.put("stocksMisAJour", stocksMisAJour);
        dto.put("mouvementsStock", mouvementsStockDto);
        if (historiqueDto != null) dto.put("historiqueVente", historiqueDto);
        if (detteCreee != null) {
            Map<String, Object> detteDto = new HashMap<>();
            detteDto.put("uuid",              detteCreee.getUuid());
            detteDto.put("montantTotal",       detteCreee.getMontantTotal());
            detteDto.put("montantRembourse",   detteCreee.getMontantRembourse());
            detteDto.put("montantRestant",     detteCreee.getMontantRestant());
            detteDto.put("statut",             detteCreee.getStatut());
            detteDto.put("clientUuid",         client.getUuid());
            detteDto.put("venteUuid",          vente.getUuid());
            detteDto.put("groupeUuid",         groupe.getUuid());
            detteDto.put("dateRemboursement",  detteCreee.getDateRemboursement());
            dto.put("dette", detteDto);
        }
        return dto;
    }

    @Transactional(readOnly = true)
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