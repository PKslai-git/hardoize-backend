package com.digneequipe.hardoize.controllers;

import com.digneequipe.hardoize.dto.response.ApiResponse;
import com.digneequipe.hardoize.services.ProduitService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/produits")
@RequiredArgsConstructor
public class ProduitController {

    private final ProduitService produitService;

    @PostMapping
    public ResponseEntity<ApiResponse<Map<String,Object>>> creer(
            @RequestBody Map<String,Object> body,
            Authentication auth) {
        try {
            return ResponseEntity.ok(ApiResponse.ok(
                    "Produit créé",
                    produitService.creerOuMettreAJour(body, auth.getName())
            ));
        } catch (Exception e) {
            return ResponseEntity.ok(ApiResponse.error(e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<Map<String,Object>>> modifier(
            @PathVariable Long id,
            @RequestBody Map<String,Object> body,
            Authentication auth) {
        try {
            return ResponseEntity.ok(ApiResponse.ok(
                    "Produit modifié",
                    produitService.creerOuMettreAJour(body, auth.getName())
            ));
        } catch (Exception e) {
            return ResponseEntity.ok(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<Map<String,Object>>>> getAll(
            @RequestParam Long groupeId,
            @RequestParam(defaultValue = "true") boolean avecUnites) {
        return ResponseEntity.ok(ApiResponse.ok(
                produitService.getByGroupe(groupeId, avecUnites)
        ));
    }
}