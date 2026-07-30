package com.digneequipe.hardoize.services;

import com.digneequipe.hardoize.models.*;
import com.digneequipe.hardoize.repositories.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
public class PermissionService {

    private final PermissionMembreRepository permissionRepo;
    private final MembreGroupeRepository     membreRepo;

    @Transactional
    public PermissionMembre creerDefaut(Long membreId) {
        MembreGroupe membre = membreRepo.findById(membreId)
                .orElseThrow(() -> new RuntimeException("Membre introuvable"));

        return permissionRepo.findByMembreId(membreId)
                .orElseGet(() -> {
                    PermissionMembre p = PermissionMembre.builder()
                            .membre(membre)
                            .peutVendre(true)
                            .peutVoirDettes(false)
                            .peutGererStock(false)
                            .peutVoirStats(false)
                            .peutGererClients(false)
                            .peutVoirHistorique(false)
                            .build();
                    return permissionRepo.save(p);
                });
    }

    @Transactional
    public Map<String, Object> modifier(
            Long membreId, Map<String, Boolean> permsBody) {

        PermissionMembre p = permissionRepo
                .findByMembreId(membreId)
                .orElseGet(() -> creerDefaut(membreId));

        if (permsBody.containsKey("peutVendre"))
            p.setPeutVendre(permsBody.get("peutVendre"));
        if (permsBody.containsKey("peutVoirDettes"))
            p.setPeutVoirDettes(permsBody.get("peutVoirDettes"));
        if (permsBody.containsKey("peutGererStock"))
            p.setPeutGererStock(permsBody.get("peutGererStock"));
        if (permsBody.containsKey("peutVoirStats"))
            p.setPeutVoirStats(permsBody.get("peutVoirStats"));
        if (permsBody.containsKey("peutGererClients"))
            p.setPeutGererClients(permsBody.get("peutGererClients"));
        if (permsBody.containsKey("peutVoirHistorique"))
            p.setPeutVoirHistorique(permsBody.get("peutVoirHistorique"));

        permissionRepo.save(p);
        return buildDto(p);
    }

    public Map<String, Object> getByMembre(Long membreId) {
        PermissionMembre p = permissionRepo
                .findByMembreId(membreId)
                .orElseGet(() -> creerDefaut(membreId));
        return buildDto(p);
    }

    @Transactional
    public PermissionMembre sauvegarder(PermissionMembre p) {
        return permissionRepo.save(p);
    }

    private Map<String, Object> buildDto(PermissionMembre p) {
        Map<String, Object> dto = new HashMap<>();
        dto.put("membreId",         p.getMembre().getId());
        dto.put("peutVendre",         p.getPeutVendre());
        dto.put("peutVoirDettes",     p.getPeutVoirDettes());
        dto.put("peutGererStock",     p.getPeutGererStock());
        dto.put("peutVoirStats",      p.getPeutVoirStats());
        dto.put("peutGererClients",   p.getPeutGererClients());
        dto.put("peutVoirHistorique", p.getPeutVoirHistorique());
        return dto;
    }

    // Dans PermissionService.java, ajoute :

    @Transactional
    public Map<String, Object> modifierParUuid(
            String membreUuid, Map<String, Boolean> permsBody) {

        MembreGroupe membre = membreRepo.findByUuid(membreUuid)
                .orElseThrow(() -> new RuntimeException("Membre introuvable"));

        return modifier(membre.getId(), permsBody);
    }

    public Map<String, Object> getByMembreUuid(String membreUuid) {
        MembreGroupe membre = membreRepo.findByUuid(membreUuid)
                .orElseThrow(() -> new RuntimeException("Membre introuvable"));

        return getByMembre(membre.getId());
    }
}