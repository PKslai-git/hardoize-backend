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