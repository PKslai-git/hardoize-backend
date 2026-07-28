package com.digneequipe.hardoize.controllers;

import com.digneequipe.hardoize.dto.response.ApiResponse;
import com.digneequipe.hardoize.services.FournisseurService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/fournisseurs")
@RequiredArgsConstructor
public class FournisseurController {

    private final FournisseurService fournisseurService;

    @PostMapping
    public ResponseEntity<ApiResponse<Map<String,Object>>> creer(
            @RequestBody Map<String,Object> body) {
        try {
            return ResponseEntity.ok(ApiResponse.ok(
                    "Fournisseur créé",
                    fournisseurService.creerOuMettreAJour(body)
            ));
        } catch (Exception e) {
            return ResponseEntity.ok(ApiResponse.error(e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<Map<String,Object>>> modifier(
            @PathVariable Long id,
            @RequestBody Map<String,Object> body) {
        try {
            return ResponseEntity.ok(ApiResponse.ok(
                    "Fournisseur modifié",
                    fournisseurService.creerOuMettreAJour(body)
            ));
        } catch (Exception e) {
            return ResponseEntity.ok(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<Map<String,Object>>>> getAll(
            @RequestParam Long groupeId) {
        return ResponseEntity.ok(ApiResponse.ok(
                fournisseurService.getByGroupe(groupeId)
        ));
    }

    @PostMapping("/dettes")
    public ResponseEntity<ApiResponse<Map<String,Object>>> creerDette(
            @RequestBody Map<String,Object> body) {
        try {
            return ResponseEntity.ok(ApiResponse.ok(
                    "Dette créée",
                    fournisseurService.creerOuMajDette(body)
            ));
        } catch (Exception e) {
            return ResponseEntity.ok(ApiResponse.error(e.getMessage()));
        }
    }

    @PutMapping("/dettes/{uuid}")
    public ResponseEntity<ApiResponse<Map<String,Object>>> majDette(
            @PathVariable String uuid,
            @RequestBody Map<String,Object> body) {
        try {
            body.put("uuid", uuid);
            return ResponseEntity.ok(ApiResponse.ok(
                    "Dette mise à jour",
                    fournisseurService.creerOuMajDette(body)
            ));
        } catch (Exception e) {
            return ResponseEntity.ok(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/dettes")
    public ResponseEntity<ApiResponse<List<Map<String,Object>>>> getDettes(
            @RequestParam Long groupeId) {
        return ResponseEntity.ok(ApiResponse.ok(
                fournisseurService.getDettesByGroupe(groupeId)
        ));
    }
}