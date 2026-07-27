package com.digneequipe.hardoize.services;

import com.digneequipe.hardoize.models.*;
import com.digneequipe.hardoize.repositories.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
public class ClientService {

    private final ClientRepository      clientRepo;
    private final GroupeRepository      groupeRepo;
    private final UtilisateurRepository utilisateurRepo;

    @Transactional
    public Map<String, Object> creerOuMettreAJour(
            Map<String, Object> body, String telephone) {

        String uuid = s(body, "uuid");
        if (uuid == null) throw new RuntimeException("UUID obligatoire");

        Client c = clientRepo.findByUuid(uuid)
                .orElse(Client.builder().uuid(uuid).build());

        c.setNomClient(s(body, "nomClient"));
        c.setNumeroClient(s(body, "numeroClient"));
        c.setEmail(s(body, "email"));
        c.setPhotoUri(s(body, "photoUri"));
        c.setScore(i(body, "score") != null ? i(body, "score") : 100);
        c.setEstActif(true);

        String gUuid = s(body, "groupeUuid");
        if (gUuid != null)
            groupeRepo.findByUuid(gUuid).ifPresent(c::setGroupe);

        utilisateurRepo.findByTelephone(telephone)
                .ifPresent(c::setUtilisateur);

        c = clientRepo.save(c);
        return buildDto(c);
    }

    public List<Map<String, Object>> getByGroupe(Long groupeId) {
        List<Client> clients =
                clientRepo.findByGroupeIdAndEstActif(groupeId, true);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Client c : clients) result.add(buildDto(c));
        return result;
    }

    private Map<String, Object> buildDto(Client c) {
        Map<String, Object> dto = new HashMap<>();
        dto.put("id",           c.getId());
        dto.put("uuid",         c.getUuid());
        dto.put("nomClient",    c.getNomClient());
        dto.put("numeroClient", c.getNumeroClient());
        dto.put("email",        c.getEmail());
        dto.put("score",        c.getScore());
        dto.put("groupeUuid",   c.getGroupe() != null
                ? c.getGroupe().getUuid() : null);
        dto.put("createdAt",    c.getCreatedAt());
        return dto;
    }

    private String  s(Map<String,Object> m, String k) {
        Object v = m.get(k); return v != null ? v.toString() : null;
    }
    private Integer i(Map<String,Object> m, String k) {
        Object v = m.get(k);
        return v != null ? Integer.parseInt(v.toString()) : null;
    }
}