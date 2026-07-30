package com.digneequipe.hardoize.services;

import com.digneequipe.hardoize.models.*;
import com.digneequipe.hardoize.repositories.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;

@Service
@RequiredArgsConstructor
public class DetteService {

    private final DetteRepository   detteRepo;
    private final ClientRepository  clientRepo;
    private final VenteRepository   venteRepo;
    private final GroupeRepository  groupeRepo;

    @Transactional
    public Map<String, Object> creerOuMaj(Map<String, Object> body) {
        String uuid = s(body, "uuid");
        if (uuid == null) throw new RuntimeException("UUID obligatoire");

        Dette d = detteRepo.findByUuid(uuid)
                .orElse(Dette.builder().uuid(uuid).build());

        d.setMontantTotal(dOrZero(body, "montantTotal"));
        d.setMontantRembourse(dOrZero(body, "montantRembourse"));
        d.setMontantRestant(
                body.containsKey("montantRestant")
                        ? dOrZero(body, "montantRestant")
                        : d.getMontantTotal() - d.getMontantRembourse()
        );
        d.setStatut(s(body, "statut") != null
                ? s(body, "statut") : "active");
        d.setPaiementsJson(s(body, "paiementsJson"));

        // DateRemboursement
        if (body.containsKey("dateRemboursement")
                && body.get("dateRemboursement") != null) {
            try {
                long ms = Long.parseLong(
                        body.get("dateRemboursement").toString());
                d.setDateRemboursement(LocalDateTime.ofInstant(
                        Instant.ofEpochMilli(ms), ZoneId.systemDefault()));
            } catch (NumberFormatException e) {
                // Ignorer
            }
        }

        String vUuid = s(body, "venteUuid");
        if (vUuid != null)
            venteRepo.findByUuid(vUuid).ifPresent(d::setVente);

        String cUuid = s(body, "clientUuid");
        if (cUuid != null)
            clientRepo.findByUuid(cUuid).ifPresent(d::setClient);

        String gUuid = s(body, "groupeUuid");
        if (gUuid != null)
            groupeRepo.findByUuid(gUuid).ifPresent(d::setGroupe);

        d = detteRepo.save(d);
        return buildDto(d);
    }

    public List<Map<String, Object>> getByGroupe(Long groupeId) {
        List<Dette> dettes = detteRepo.findByGroupeId(groupeId);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Dette d : dettes) result.add(buildDto(d));
        return result;
    }

    @Transactional
    public Map<String, Object> rembourser(String uuid, double montant) {
        Dette d = detteRepo.findByUuid(uuid)
                .orElseThrow(() -> new RuntimeException("Dette introuvable"));

        d.setMontantRembourse(d.getMontantRembourse() + montant);
        d.setMontantRestant(
                Math.max(0, d.getMontantTotal() - d.getMontantRembourse()));
        if (d.getMontantRestant() <= 0) {
            d.setStatut("soldee");
            d.setDateSolde(LocalDateTime.now());
        }

        d = detteRepo.save(d);
        return buildDto(d);
    }

    private Map<String, Object> buildDto(Dette d) {
        Map<String, Object> dto = new HashMap<>();
        dto.put("id",              d.getId());
        dto.put("uuid",            d.getUuid());
        dto.put("montantTotal",    d.getMontantTotal());
        dto.put("montantRembourse",d.getMontantRembourse());
        dto.put("montantRestant",  d.getMontantRestant());
        dto.put("statut",          d.getStatut());
        dto.put("clientUuid",      d.getClient() != null
                ? d.getClient().getUuid() : null);
        dto.put("venteUuid",       d.getVente() != null
                ? d.getVente().getUuid() : null);
        dto.put("groupeUuid",      d.getGroupe() != null
                ? d.getGroupe().getUuid() : null);
        dto.put("createdAt",       d.getCreatedAt());
        return dto;
    }

    private String s(Map<String,Object> m, String k) {
        Object v = m.get(k); return v != null ? v.toString() : null;
    }
    private double dOrZero(Map<String,Object> m, String k) {
        Object v = m.get(k);
        return v != null ? Double.parseDouble(v.toString()) : 0.0;
    }
}