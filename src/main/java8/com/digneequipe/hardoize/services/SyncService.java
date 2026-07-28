package com.digneequipe.hardoize.services;

import com.digneequipe.hardoize.dto.request.SyncBatchRequest;
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
public class SyncService {

    private final UtilisateurRepository    utilisateurRepo;
    private final GroupeRepository         groupeRepo;
    private final MembreGroupeRepository   membreRepo;
    private final PermissionMembreRepository permissionRepo;
    private final FournisseurRepository    fournisseurRepo;
    private final ProduitRepository        produitRepo;
    private final ClientRepository         clientRepo;
    private final VenteRepository          venteRepo;
    private final LigneVenteRepository     ligneVenteRepo;
    private final DetteRepository          detteRepo;
    private final DetteFournisseurRepository detteFournisseurRepo;
    private final MouvementStockRepository mouvementRepo;
    private final HistoriqueVenteRepository  historiqueVenteRepo;
    private final HistoriquePaiementRepository historiquePaiementRepo;
    private final UniteProduitRepository uniteProduitRepo;

    // ── Helpers ────────────────────────────────────────────────
    private String  s(Map<String,Object> m, String k) {
        Object v = m.get(k); return v != null ? v.toString() : null;
    }
    private Double  d(Map<String,Object> m, String k) {
        Object v = m.get(k); return v != null ? Double.parseDouble(v.toString()) : null;
    }
    private Integer i(Map<String,Object> m, String k) {
        Object v = m.get(k); return v != null ? Integer.parseInt(v.toString()) : null;
    }
    private Boolean b(Map<String,Object> m, String k) {
        Object v = m.get(k); return v != null ? Boolean.parseBoolean(v.toString()) : null;
    }
    private LocalDateTime dt(Map<String,Object> m, String k) {
        String val = s(m, k);
        if (val == null) return null;
        try {
            long ms = Long.parseLong(val);
            return LocalDateTime.ofInstant(
                Instant.ofEpochMilli(ms), ZoneId.systemDefault());
        } catch (NumberFormatException e) {
            try {
                return LocalDate.parse(val,
                    DateTimeFormatter.ofPattern("dd/MM/yyyy")).atTime(23,59,59);
            } catch (Exception ex) { return null; }
        }
    }

    // ── Sync Batch principal (Mode Solo) ───────────────────────
    @Transactional
    public Map<String, Object> syncBatch(SyncBatchRequest req, String telephone) {
        Utilisateur user = utilisateurRepo.findByTelephone(telephone)
            .orElseThrow(() -> new RuntimeException("Utilisateur introuvable"));

        // Map UUID local → ID serveur (pour résoudre les FK)
        Map<String,Long> idMap = new HashMap<>();
        int total = 0;

        // Ordre strict : respect des dépendances FK
        total += syncGroupes(req.getGroupes(), user, idMap);
        total += syncMembres(req.getMembres(), user, idMap);
        total += syncFournisseurs(req.getFournisseurs(), user, idMap);
        total += syncProduits(req.getProduits(), user, idMap);
        total += syncUnitesProduit(req.getUnitesProduit(), idMap);
        total += syncClients(req.getClients(), user, idMap);
        total += syncVentesEtLignes(req.getVentes(), req.getLignesVentes(), user, idMap);
        total += syncDettes(req.getDettes(), user, idMap);
        total += syncDettesFournisseurs(req.getDettesFournisseurs(), idMap);
        total += syncMouvements(req.getMouvementsStock(), user, idMap);
        total += syncHistoriqueVentes(req.getHistoriqueVentes(), idMap);
        total += syncHistoriquePaiements(req.getHistoriquePaiements(), idMap);

        Map<String,Object> result = new HashMap<>();
        result.put("synced",    total);
        result.put("idMap",     idMap);
        result.put("timestamp", LocalDateTime.now().toString());
        return result;
    }

    // 1) Groupes
    private int syncGroupes(List<Map<String,Object>> items,
                             Utilisateur user, Map<String,Long> idMap) {
        if (items == null) return 0;
        int n = 0;
        for (Map<String,Object> m : items) {
            String uuid = s(m, "uuid");
            if (uuid == null) continue;
            try {
                Groupe g = groupeRepo.findByUuid(uuid)
                    .orElse(Groupe.builder().uuid(uuid).build());
                g.setNom(s(m,"nom"));
                g.setDescription(s(m,"description"));
                g.setCodeQR(s(m,"codeQR"));
                g.setHeureFermeture(
                    s(m,"heureFermeture") != null ? s(m,"heureFermeture") : "18:00");
                g.setMode(s(m,"mode") != null ? s(m,"mode") : "solo");
                g.setProprietaire(user);
                g.setEstActif(true);
                g = groupeRepo.save(g);
                idMap.put(uuid, g.getId());
                n++;
            } catch (Exception e) {
                log.error("Sync groupe uuid={}: {}", uuid, e.getMessage());
            }
        }
        return n;
    }

    // 2) Membres
    private int syncMembres(List<Map<String,Object>> items,
                             Utilisateur user, Map<String,Long> idMap) {
        if (items == null) return 0;
        int n = 0;
        for (Map<String,Object> m : items) {
            String uuid = s(m,"uuid");
            if (uuid == null) continue;
            try {
                MembreGroupe mg = membreRepo.findByUuid(uuid)
                    .orElse(MembreGroupe.builder().uuid(uuid).build());
                mg.setNomAffiche(s(m,"nomAffiche"));
                mg.setTelephone(s(m,"telephone"));
                mg.setRole(s(m,"role") != null ? s(m,"role") : "vendeur");
                mg.setBailHeure(s(m,"bailHeure") != null ? s(m,"bailHeure") : "18:00");
                mg.setConnexionPermanente(
                    b(m,"connexionPermanente") != null && b(m,"connexionPermanente"));
                mg.setUtilisateur(user);
                // Résoudre groupe FK
                String gUuid = s(m,"groupeUuid");
                if (gUuid != null) {
                    Long gId = idMap.get(gUuid);
                    if (gId != null) groupeRepo.findById(gId).ifPresent(mg::setGroupe);
                    else groupeRepo.findByUuid(gUuid).ifPresent(mg::setGroupe);
                }
                mg = membreRepo.save(mg);
                idMap.put(uuid, mg.getId());
                n++;
            } catch (Exception e) {
                log.error("Sync membre uuid={}: {}", uuid, e.getMessage());
            }
        }
        return n;
    }

    // 3) Fournisseurs
    private int syncFournisseurs(List<Map<String,Object>> items,
                                  Utilisateur user, Map<String,Long> idMap) {
        if (items == null) return 0;
        int n = 0;
        for (Map<String,Object> m : items) {
            String uuid = s(m,"uuid");
            if (uuid == null) continue;
            try {
                Fournisseur f = fournisseurRepo.findByUuid(uuid)
                    .orElse(Fournisseur.builder().uuid(uuid).build());
                f.setNom(s(m,"nom"));
                f.setTelephone(s(m,"telephone"));
                f.setEmail(s(m,"email"));
                f.setAdresse(s(m,"adresse"));
                f.setPhotoUri(s(m,"photoUri"));
                f.setEstActif(true);
                resolveGroupe(m, f, idMap);
                f = fournisseurRepo.save(f);
                idMap.put(uuid, f.getId());
                n++;
            } catch (Exception e) {
                log.error("Sync fournisseur uuid={}: {}", uuid, e.getMessage());
            }
        }
        return n;
    }

    // 4) Produits
    private int syncProduits(List<Map<String,Object>> items,
                              Utilisateur user, Map<String,Long> idMap) {
        if (items == null) return 0;
        int n = 0;
        for (Map<String,Object> m : items) {
            String uuid = s(m,"uuid");
            if (uuid == null) continue;
            try {
                Produit p = produitRepo.findByUuid(uuid)
                    .orElse(Produit.builder().uuid(uuid).build());
                p.setNom(s(m,"nom"));
                p.setCategorie(s(m,"categorie"));
                p.setPrixAchat(d(m,"prixAchat") != null ? d(m,"prixAchat") : 0.0);
                p.setPrixVente(d(m,"prixVente") != null ? d(m,"prixVente") : 0.0);
                p.setQuantiteStock(i(m,"quantiteStock") != null ? i(m,"quantiteStock") : 0);
                p.setStockMinimum(i(m,"stockMinimum") != null ? i(m,"stockMinimum") : 5);
                p.setPhotoUri(s(m,"photoUri"));
                p.setUtilisateur(user);
                p.setEstActif(true);
                resolveGroupe(m, p, idMap);
                // Fournisseur FK
                String fUuid = s(m,"fournisseurUuid");
                if (fUuid != null) {
                    Long fId = idMap.get(fUuid);
                    if (fId != null) fournisseurRepo.findById(fId).ifPresent(p::setFournisseur);
                    else fournisseurRepo.findByUuid(fUuid).ifPresent(p::setFournisseur);
                }
                p = produitRepo.save(p);
                idMap.put(uuid, p.getId());
                n++;
            } catch (Exception e) {
                log.error("Sync produit uuid={}: {}", uuid, e.getMessage());
            }
        }
        return n;
    }

    // 5) Clients
    private int syncClients(List<Map<String,Object>> items,
                             Utilisateur user, Map<String,Long> idMap) {
        if (items == null) return 0;
        int n = 0;
        for (Map<String,Object> m : items) {
            String uuid = s(m,"uuid");
            if (uuid == null) continue;
            try {
                Client c = clientRepo.findByUuid(uuid)
                    .orElse(Client.builder().uuid(uuid).build());
                c.setNomClient(s(m,"nomClient"));
                c.setNumeroClient(s(m,"numeroClient"));
                c.setEmail(s(m,"email"));
                c.setPhotoUri(s(m,"photoUri"));
                c.setScore(i(m,"score") != null ? i(m,"score") : 100);
                c.setUtilisateur(user);
                c.setEstActif(true);
                resolveGroupe(m, c, idMap);
                c = clientRepo.save(c);
                idMap.put(uuid, c.getId());
                n++;
            } catch (Exception e) {
                log.error("Sync client uuid={}: {}", uuid, e.getMessage());
            }
        }
        return n;
    }

    // 6) Ventes + Lignes (atomique)
    private int syncVentesEtLignes(List<Map<String,Object>> ventes,
                                    List<Map<String,Object>> lignes,
                                    Utilisateur user, Map<String,Long> idMap) {
        int n = 0;
        n += syncVentes(ventes, user, idMap);
        n += syncLignesVentes(lignes, idMap);
        return n;
    }

    private int syncVentes(List<Map<String,Object>> items,
                            Utilisateur user, Map<String,Long> idMap) {
        if (items == null) return 0;
        int n = 0;
        for (Map<String,Object> m : items) {
            String uuid = s(m,"uuid");
            if (uuid == null) continue;
            try {
                // Idempotent : si déjà synced, juste mettre à jour idMap
                Optional<Vente> existing = venteRepo.findByUuid(uuid);
                if (existing.isPresent()) {
                    idMap.put(uuid, existing.get().getId());
                    n++;
                    continue;
                }
                Vente v = Vente.builder().uuid(uuid).build();
                v.setMontantTotal(d(m,"montantTotal") != null ? d(m,"montantTotal") : 0.0);
                v.setBeneficeNet(d(m,"beneficeNet") != null ? d(m,"beneficeNet") : 0.0);
                v.setTypePaiement(s(m,"typePaiement") != null ? s(m,"typePaiement") : "especes");
                v.setUtilisateur(user);
                resolveGroupe(m, v, idMap);
                // Client FK
                String cUuid = s(m,"clientUuid");
                if (cUuid != null) {
                    Long cId = idMap.get(cUuid);
                    if (cId != null) clientRepo.findById(cId).ifPresent(v::setClient);
                    else clientRepo.findByUuid(cUuid).ifPresent(v::setClient);
                }
                v = venteRepo.save(v);
                idMap.put(uuid, v.getId());
                n++;
            } catch (Exception e) {
                log.error("Sync vente uuid={}: {}", uuid, e.getMessage());
            }
        }
        return n;
    }

    private int syncLignesVentes(List<Map<String,Object>> items,
                                  Map<String,Long> idMap) {
        if (items == null) return 0;
        int n = 0;
        for (Map<String,Object> m : items) {
            String uuid = s(m,"uuid");
            if (uuid == null) continue;
            try {
                if (ligneVenteRepo.existsByUuid(uuid)) { n++; continue; }
                // Résoudre vente parente
                String vUuid = s(m,"venteUuid");
                Vente vente = null;
                if (vUuid != null) {
                    Long vId = idMap.get(vUuid);
                    if (vId != null) vente = venteRepo.findById(vId).orElse(null);
                    if (vente == null) vente = venteRepo.findByUuid(vUuid).orElse(null);
                }
                if (vente == null) {
                    log.warn("Ligne vente uuid={}: vente introuvable", uuid);
                    continue;
                }
                LigneVente l = LigneVente.builder().uuid(uuid).build();
                l.setVente(vente);
                l.setNomProduit(s(m,"nomProduit"));
                l.setQuantite(i(m,"quantite") != null ? i(m,"quantite") : 1);
                l.setPrixAchat(d(m,"prixAchat") != null ? d(m,"prixAchat") : 0.0);
                l.setPrixUnitaire(d(m,"prixUnitaire") != null ? d(m,"prixUnitaire") : 0.0);
                l.setSousTotal(d(m,"sousTotal") != null ? d(m,"sousTotal") : 0.0);
                l.setMarge(d(m,"marge") != null ? d(m,"marge") : 0.0);
                // Produit FK
                String pUuid = s(m,"produitUuid");
                if (pUuid != null) {
                    Long pId = idMap.get(pUuid);
                    if (pId != null) produitRepo.findById(pId).ifPresent(l::setProduit);
                    else produitRepo.findByUuid(pUuid).ifPresent(l::setProduit);
                }
                ligneVenteRepo.save(l);
                n++;
            } catch (Exception e) {
                log.error("Sync ligne vente uuid={}: {}", uuid, e.getMessage());
            }
        }
        return n;
    }

    // 7) Dettes
    private int syncDettes(List<Map<String,Object>> items,
                            Utilisateur user, Map<String,Long> idMap) {
        if (items == null) return 0;
        int n = 0;
        for (Map<String,Object> m : items) {
            String uuid = s(m,"uuid");
            if (uuid == null) continue;
            try {
                Dette d = detteRepo.findByUuid(uuid)
                    .orElse(Dette.builder().uuid(uuid).build());
                d.setMontantTotal(dOrZero(m,"montantTotal"));
                d.setMontantRembourse(dOrZero(m,"montantRembourse"));
                d.setMontantRestant(dOrZero(m,"montantRestant"));
                d.setStatut(s(m,"statut") != null ? s(m,"statut") : "active");
                d.setDateRemboursement(dt(m,"dateRemboursement"));
                d.setPaiementsJson(s(m,"paiementsJson"));
                d.setUtilisateur(user);
                resolveGroupe(m, d, idMap);
                // Client FK
                final Dette dette = d;

                resolveFk(m,"clientUuid", idMap,
                        id -> clientRepo.findById(id).ifPresent(dette::setClient),
                        u  -> clientRepo.findByUuid(u).ifPresent(dette::setClient));

                resolveFk(m,"venteUuid", idMap,
                        id -> venteRepo.findById(id).ifPresent(dette::setVente),
                        u  -> venteRepo.findByUuid(u).ifPresent(dette::setVente));

                d = detteRepo.save(dette);
                idMap.put(uuid, d.getId());
                n++;
            } catch (Exception e) {
                log.error("Sync dette uuid={}: {}", uuid, e.getMessage());
            }
        }
        return n;
    }

    // 8) Dettes fournisseurs
    private int syncDettesFournisseurs(List<Map<String,Object>> items,
                                        Map<String,Long> idMap) {
        if (items == null) return 0;
        int n = 0;
        for (Map<String,Object> m : items) {
            String uuid = s(m,"uuid");
            if (uuid == null) continue;
            try {
                DetteFournisseur df = detteFournisseurRepo.findByUuid(uuid)
                    .orElse(DetteFournisseur.builder().uuid(uuid).build());
                df.setNomFournisseur(s(m,"nomFournisseur"));
                df.setMontantTotal(dOrZero(m,"montantTotal"));
                df.setMontantRembourse(dOrZero(m,"montantRembourse"));
                df.setMontantRestant(dOrZero(m,"montantRestant"));
                df.setMotif(s(m,"motif"));
                df.setStatut(s(m,"statut") != null ? s(m,"statut") : "active");
                df.setDateRemboursement(dt(m,"dateRemboursement"));
                df.setPaiementsJson(s(m,"paiementsJson"));
                resolveGroupe(m, df, idMap);
                final DetteFournisseur detteFournisseur = df;

                resolveFk(m,"fournisseurUuid", idMap,
                        id -> fournisseurRepo.findById(id).ifPresent(detteFournisseur::setFournisseur),
                        u  -> fournisseurRepo.findByUuid(u).ifPresent(detteFournisseur::setFournisseur));

                df = detteFournisseurRepo.save(detteFournisseur);
                idMap.put(uuid, df.getId());
                n++;
            } catch (Exception e) {
                log.error("Sync dette fourn uuid={}: {}", uuid, e.getMessage());
            }
        }
        return n;
    }

    // 9) Mouvements stock
    private int syncMouvements(List<Map<String,Object>> items,
                                Utilisateur user, Map<String,Long> idMap) {
        if (items == null) return 0;
        int n = 0;
        for (Map<String,Object> m : items) {
            String uuid = s(m,"uuid");
            if (uuid == null) continue;
            try {
                if (mouvementRepo.existsByUuid(uuid)) { n++; continue; }
                MouvementStock mv = MouvementStock.builder().uuid(uuid).build();
                mv.setNomProduit(s(m,"nomProduit"));
                mv.setType(s(m,"type"));
                mv.setMotif(s(m,"motif"));
                mv.setQuantite(i(m,"quantite") != null ? i(m,"quantite") : 0);
                mv.setPrixUnitaire(dOrZero(m,"prixUnitaire"));
                mv.setMontantTotal(dOrZero(m,"montantTotal"));
                mv.setMontantPaye(dOrZero(m,"montantPaye"));
                mv.setModePaiement(s(m,"modePaiement"));
                mv.setUtilisateur(user);
                resolveGroupe(m, mv, idMap);
                resolveFk(m,"produitUuid", idMap,
                    id -> produitRepo.findById(id).ifPresent(mv::setProduit),
                    u  -> produitRepo.findByUuid(u).ifPresent(mv::setProduit));
                resolveFk(m,"fournisseurUuid", idMap,
                    id -> fournisseurRepo.findById(id).ifPresent(mv::setFournisseur),
                    u  -> fournisseurRepo.findByUuid(u).ifPresent(mv::setFournisseur));
                mouvementRepo.save(mv);
                n++;
            } catch (Exception e) {
                log.error("Sync mouvement uuid={}: {}", uuid, e.getMessage());
            }
        }
        return n;
    }

    // 10) Historique ventes
    private int syncHistoriqueVentes(List<Map<String,Object>> items,
                                      Map<String,Long> idMap) {
        if (items == null) return 0;
        int n = 0;
        for (Map<String,Object> m : items) {
            String uuid = s(m,"uuid");
            if (uuid == null) continue;
            try {
                HistoriqueVente h = historiqueVenteRepo.findByUuid(uuid)
                    .orElse(HistoriqueVente.builder().uuid(uuid).build());
                h.setDate(s(m,"date"));
                h.setTotalVentes(dOrZero(m,"totalVentes"));
                h.setTotalEspeces(dOrZero(m,"totalEspeces"));
                h.setTotalCredit(dOrZero(m,"totalCredit"));
                h.setBeneficeNet(dOrZero(m,"beneficeNet"));
                h.setNbVentes(i(m,"nbVentes") != null ? i(m,"nbVentes") : 0);
                resolveGroupe(m, h, idMap);
                h = historiqueVenteRepo.save(h);
                idMap.put(uuid, h.getId());
                n++;
            } catch (Exception e) {
                log.error("Sync hist vente uuid={}: {}", uuid, e.getMessage());
            }
        }
        return n;
    }

    // 11) Historique paiements
    private int syncHistoriquePaiements(List<Map<String,Object>> items,
                                         Map<String,Long> idMap) {
        if (items == null) return 0;
        int n = 0;
        for (Map<String,Object> m : items) {
            String uuid = s(m,"uuid");
            if (uuid == null) continue;
            try {
                if (historiquePaiementRepo.existsByUuid(uuid)) { n++; continue; }
                HistoriquePaiement hp = HistoriquePaiement.builder().uuid(uuid).build();
                hp.setType(s(m,"type"));
                hp.setSens(s(m,"sens"));
                hp.setMontant(dOrZero(m,"montant"));
                hp.setDescription(s(m,"description"));
                hp.setNomClient(s(m,"nomClient"));
                hp.setNomFournisseur(s(m,"nomFournisseur"));
                resolveGroupe(m, hp, idMap);
                historiquePaiementRepo.save(hp);
                n++;
            } catch (Exception e) {
                log.error("Sync hist paiement uuid={}: {}", uuid, e.getMessage());
            }
        }
        return n;
    }

    // Méthode syncUnitesProduit :
    private int syncUnitesProduit(List<Map<String, Object>> items,
                                  Map<String, Long> idMap) {
        if (items == null) return 0;
        int n = 0;
        for (Map<String, Object> m : items) {
            String uuid = s(m, "uuid");
            if (uuid == null) continue;
            try {
                // findByUuid = upsert idempotent
                UniteProduit u = uniteProduitRepo.findByUuid(uuid)
                        .orElse(UniteProduit.builder().uuid(uuid).build());

                u.setNom(s(m, "nom"));
                u.setFacteur(d(m, "facteur") != null ? d(m,"facteur") : 1.0);
                u.setPrixAchat(d(m, "prixAchat") != null ? d(m,"prixAchat") : 0.0);
                u.setPrixVente(d(m, "prixVente") != null ? d(m,"prixVente") : 0.0);
                u.setEstBase(b(m, "estBase") != null && b(m,"estBase"));
                u.setEstReference(b(m,"estReference") != null && b(m,"estReference"));
                u.setOrdre(i(m,"ordre") != null ? i(m,"ordre") : 0);

                String pUuid = s(m,"produitUuid");
                if (pUuid != null) {
                    Long pId = idMap.get(pUuid);
                    if (pId != null)
                        produitRepo.findById(pId).ifPresent(u::setProduit);
                    else
                        produitRepo.findByUuid(pUuid).ifPresent(u::setProduit);
                }
                uniteProduitRepo.save(u);
                n++;
            } catch (Exception e) {
                log.error("Sync unité produit uuid={}: {}", uuid, e.getMessage());
            }
        }
        return n;
    }
    // ── Helpers FK ─────────────────────────────────────────────
    private double dOrZero(Map<String,Object> m, String k) {
        Double v = d(m,k); return v != null ? v : 0.0;
    }

    @FunctionalInterface
    interface IdConsumer  { void accept(Long id); }
    @FunctionalInterface
    interface UuidConsumer { void accept(String uuid); }

    private void resolveFk(Map<String,Object> m, String key,
                            Map<String,Long> idMap,
                            IdConsumer byId, UuidConsumer byUuid) {
        String uuid = s(m, key);
        if (uuid == null) return;
        Long id = idMap.get(uuid);
        if (id != null) byId.accept(id);
        else byUuid.accept(uuid);
    }

    // Résoudre la FK groupe via groupeUuid
    private <T> void resolveGroupe(Map<String,Object> m, T entity,
                                    Map<String,Long> idMap) {
        String gUuid = s(m,"groupeUuid");
        if (gUuid == null) return;
        Long gId = idMap.get(gUuid);
        Optional<Groupe> groupe = gId != null
            ? groupeRepo.findById(gId)
            : groupeRepo.findByUuid(gUuid);
        groupe.ifPresent(g -> {
            try {
                var method = entity.getClass().getMethod("setGroupe", Groupe.class);
                method.invoke(entity, g);
            } catch (Exception ignored) {}
        });
    }

    public boolean existsByUuid(String uuid) {
        return groupeRepo.existsByUuid(uuid)    ||
               produitRepo.existsByUuid(uuid)   ||
               clientRepo.existsByUuid(uuid)    ||
               venteRepo.existsByUuid(uuid)     ||
               detteRepo.existsByUuid(uuid)     ||
               mouvementRepo.existsByUuid(uuid) ||
               fournisseurRepo.existsByUuid(uuid);
    }
}