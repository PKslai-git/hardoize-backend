package com.digneequipe.hardoize.controllers;

import com.digneequipe.hardoize.dto.response.ApiResponse;
import com.digneequipe.hardoize.services.DetteFournisseurService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/dettes-fournisseurs")
@RequiredArgsConstructor
public class DetteFournisseurController {

    private final DetteFournisseurService service;

    @PostMapping
    public ResponseEntity<ApiResponse<Map<String,Object>>> creer(
            @RequestBody Map<String,Object> body) {
        try {
            return ResponseEntity.ok(ApiResponse.ok(
                    "Dette fournisseur créée",
                    service.creerOuMaj(body)
            ));
        } catch (Exception e) {
            return ResponseEntity.ok(ApiResponse.error(e.getMessage()));
        }
    }

    @PutMapping("/{uuid}")
    public ResponseEntity<ApiResponse<Map<String,Object>>> maj(
            @PathVariable String uuid,
            @RequestBody Map<String,Object> body) {
        try {
            body.put("uuid", uuid);
            return ResponseEntity.ok(ApiResponse.ok(
                    "Dette mise à jour",
                    service.creerOuMaj(body)
            ));
        } catch (Exception e) {
            return ResponseEntity.ok(ApiResponse.error(e.getMessage()));
        }
    }

    @PatchMapping("/{uuid}/rembourser")
    public ResponseEntity<ApiResponse<Map<String,Object>>> rembourser(
            @PathVariable String uuid,
            @RequestBody Map<String,Object> body) {
        try {
            double montant = Double.parseDouble(
                    body.get("montant").toString());
            return ResponseEntity.ok(ApiResponse.ok(
                    "Remboursement enregistré",
                    service.rembourser(uuid, montant)
            ));
        } catch (Exception e) {
            return ResponseEntity.ok(ApiResponse.error(e.getMessage()));
        }
    }

    @PatchMapping("/{uuid}/solder")
    public ResponseEntity<ApiResponse<Map<String,Object>>> solder(
            @PathVariable String uuid) {
        try {
            return ResponseEntity.ok(ApiResponse.ok(
                    "Dette soldée",
                    service.solder(uuid)
            ));
        } catch (Exception e) {
            return ResponseEntity.ok(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<Map<String,Object>>>> getAll(
            @RequestParam Long groupeId) {
        return ResponseEntity.ok(ApiResponse.ok(
                service.getByGroupe(groupeId)
        ));
    }
}