package com.digneequipe.hardoize.services;

import com.digneequipe.hardoize.models.*;
import com.digneequipe.hardoize.repositories.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
public class HistoriqueService {

    private final HistoriqueVenteRepository   histVenteRepo;
    private final HistoriquePaiementRepository histPaiementRepo;
    private final GroupeRepository            groupeRepo;
    private final ClientRepository            clientRepo;
    private final FournisseurRepository       fournisseurRepo;
    private final DetteRepository             detteRepo;

    // ── Historique ventes ──────────────────────────────────────
    @Transactional
    public Map<String, Object> creerOuMajHistoriqueVente(
            Map<String, Object> body) {
        String uuid = s(body, "uuid");
        if (uuid == null) throw new RuntimeException("UUID obligatoire");

        HistoriqueVente h = histVenteRepo.findByUuid(uuid)
                .orElse(HistoriqueVente.builder().uuid(uuid).build());

        h.setDate(s(body, "date"));
        h.setTotalVentes(dz(body, "totalVentes"));
        h.setTotalEspeces(dz(body, "totalEspeces"));
        h.setTotalCredit(dz(body, "totalCredit"));
        h.setBeneficeNet(dz(body, "beneficeNet"));
        h.setNbVentes(i(body, "nbVentes") != null ? i(body, "nbVentes") : 0);

        String gUuid = s(body, "groupeUuid");
        if (gUuid != null)
            groupeRepo.findByUuid(gUuid).ifPresent(h::setGroupe);

        h = histVenteRepo.save(h);
        return buildVenteDto(h);
    }

    // ── Historique ventes : incrément atomique (Mode Multi) ────
    // BUG CORRIGÉ : VenteService.enregistrerMulti ne touchait jamais
    // historique_ventes — la fonctionnalité "Historique" restait vide
    // en mode multi (aucune mise à jour locale sur l'appareil vendeur,
    // et donc rien à diffuser/synchroniser aux autres). Contrairement
    // au flux solo (chaque appareil incrémente sa propre ligne locale
    // au fil de l'eau, puis SyncService.syncHistoriqueVentes pousse un
    // upsert par uuid), le mode multi a besoin d'une source de vérité
    // UNIQUE et atomique — sinon deux ventes simultanées sur deux
    // appareils écraseraient le total de l'un ou l'autre au lieu de
    // s'additionner. uuid déterministe (groupe+date), verrou pessimiste
    // identique à celui du stock (ProduitRepository) : la ligne du
    // jour est verrouillée pour toute la transaction, donc deux ventes
    // concurrentes sont appliquées l'une après l'autre, jamais perdues.
    @Transactional
    public Map<String, Object> incrementerVenteMulti(
            Long groupeId, String groupeUuid,
            double montant, double benefice, String typePaiement) {
        if (groupeId == null) return null;

        String date = java.time.LocalDate.now(java.time.ZoneOffset.UTC).toString();
        HistoriqueVente h = histVenteRepo
                .findByGroupeIdAndDatePourMiseAJour(groupeId, date)
                .orElse(null);

        if (h == null) {
            h = HistoriqueVente.builder()
                    .uuid("hist-" + groupeUuid + "-" + date)
                    .date(date)
                    .totalVentes(0.0).totalEspeces(0.0).totalCredit(0.0)
                    .beneficeNet(0.0).nbVentes(0)
                    .build();
            groupeRepo.findById(groupeId).ifPresent(h::setGroupe);
        }

        h.setTotalVentes(h.getTotalVentes() + montant);
        h.setBeneficeNet(h.getBeneficeNet() + benefice);
        h.setNbVentes(h.getNbVentes() + 1);
        if ("credit".equals(typePaiement)) {
            h.setTotalCredit(h.getTotalCredit() + montant);
        } else {
            h.setTotalEspeces(h.getTotalEspeces() + montant);
        }

        h = histVenteRepo.save(h);
        return buildVenteDto(h);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getHistoriqueVentes(Long groupeId) {
        List<HistoriqueVente> list =
                histVenteRepo.findByGroupeIdOrderByDateDesc(groupeId);
        List<Map<String, Object>> result = new ArrayList<>();
        for (HistoriqueVente h : list) result.add(buildVenteDto(h));
        return result;
    }

    // ── Historique paiements ───────────────────────────────────
    @Transactional
    public Map<String, Object> enregistrerPaiement(
            Map<String, Object> body) {
        String uuid = s(body, "uuid");
        if (uuid == null) throw new RuntimeException("UUID obligatoire");
        if (histPaiementRepo.existsByUuid(uuid))
            return histPaiementRepo.findByUuid(uuid)
                    .map(this::buildPaiementDto).orElse(Map.of());

        HistoriquePaiement p = HistoriquePaiement.builder()
                .uuid(uuid)
                .type(s(body, "type") != null ? s(body, "type") : "client")
                .sens(s(body, "sens") != null ? s(body, "sens") : "entrant")
                .montant(dz(body, "montant"))
                .description(s(body, "description"))
                .nomClient(s(body, "nomClient"))
                .nomFournisseur(s(body, "nomFournisseur"))
                .build();

        String gUuid = s(body, "groupeUuid");
        if (gUuid != null)
            groupeRepo.findByUuid(gUuid).ifPresent(p::setGroupe);

        String cUuid = s(body, "clientUuid");
        if (cUuid != null)
            clientRepo.findByUuid(cUuid).ifPresent(p::setClient);

        String fUuid = s(body, "fournisseurUuid");
        if (fUuid != null)
            fournisseurRepo.findByUuid(fUuid).ifPresent(p::setFournisseur);

        p = histPaiementRepo.save(p);
        return buildPaiementDto(p);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getHistoriquePaiements(
            Long groupeId) {
        List<HistoriquePaiement> list =
                histPaiementRepo.findByGroupeIdOrderByCreatedAtDesc(groupeId);
        List<Map<String, Object>> result = new ArrayList<>();
        for (HistoriquePaiement p : list) result.add(buildPaiementDto(p));
        return result;
    }

    // ── DTOs ───────────────────────────────────────────────────
    private Map<String, Object> buildVenteDto(HistoriqueVente h) {
        Map<String, Object> dto = new HashMap<>();
        dto.put("id",           h.getId());
        dto.put("uuid",         h.getUuid());
        dto.put("date",         h.getDate());
        dto.put("totalVentes",  h.getTotalVentes());
        dto.put("totalEspeces", h.getTotalEspeces());
        dto.put("totalCredit",  h.getTotalCredit());
        dto.put("beneficeNet",  h.getBeneficeNet());
        dto.put("nbVentes",     h.getNbVentes());
        dto.put("groupeUuid",   h.getGroupe() != null
                ? h.getGroupe().getUuid() : null);
        dto.put("createdAt",    h.getCreatedAt());
        return dto;
    }

    private Map<String, Object> buildPaiementDto(HistoriquePaiement p) {
        Map<String, Object> dto = new HashMap<>();
        dto.put("id",            p.getId());
        dto.put("uuid",          p.getUuid());
        dto.put("type",          p.getType());
        dto.put("sens",          p.getSens());
        dto.put("montant",       p.getMontant());
        dto.put("description",   p.getDescription());
        dto.put("nomClient",     p.getNomClient());
        dto.put("nomFournisseur",p.getNomFournisseur());
        dto.put("groupeUuid",    p.getGroupe() != null
                ? p.getGroupe().getUuid() : null);
        dto.put("createdAt",     p.getCreatedAt());
        return dto;
    }

    // ── Helpers ────────────────────────────────────────────────
    private String s(Map<String,Object> m, String k) {
        Object v = m.get(k); return v != null ? v.toString() : null;
    }
    private double dz(Map<String,Object> m, String k) {
        Object v = m.get(k);
        return v != null ? Double.parseDouble(v.toString()) : 0.0;
    }
    private Integer i(Map<String,Object> m, String k) {
        Object v = m.get(k);
        return v != null ? Integer.parseInt(v.toString()) : null;
    }
}