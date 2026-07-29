package com.digneequipe.hardoize.controllers;

import com.digneequipe.hardoize.dto.response.ApiResponse;
import com.digneequipe.hardoize.services.HistoriqueService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class HistoriqueController {

    private final HistoriqueService historiqueService;

    // ── Historique ventes ──────────────────────────────────────
    @PostMapping("/historique-ventes")
    public ResponseEntity<ApiResponse<Map<String,Object>>> creerHV(
            @RequestBody Map<String,Object> body) {
        try {
            return ResponseEntity.ok(ApiResponse.ok(
                    "Historique vente enregistré",
                    historiqueService.creerOuMajHistoriqueVente(body)
            ));
        } catch (Exception e) {
            return ResponseEntity.ok(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/historique-ventes")
    public ResponseEntity<ApiResponse<List<Map<String,Object>>>> getHV(
            @RequestParam Long groupeId) {
        return ResponseEntity.ok(ApiResponse.ok(
                historiqueService.getHistoriqueVentes(groupeId)
        ));
    }

    // ── Historique paiements ───────────────────────────────────
    @PostMapping("/historique-paiements")
    public ResponseEntity<ApiResponse<Map<String,Object>>> creerHP(
            @RequestBody Map<String,Object> body) {
        try {
            return ResponseEntity.ok(ApiResponse.ok(
                    "Paiement enregistré",
                    historiqueService.enregistrerPaiement(body)
            ));
        } catch (Exception e) {
            return ResponseEntity.ok(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/historique-paiements")
    public ResponseEntity<ApiResponse<List<Map<String,Object>>>> getHP(
            @RequestParam Long groupeId) {
        return ResponseEntity.ok(ApiResponse.ok(
                historiqueService.getHistoriquePaiements(groupeId)
        ));
    }
}