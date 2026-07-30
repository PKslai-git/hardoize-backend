package com.digneequipe.hardoize.controllers;

import com.digneequipe.hardoize.dto.request.SyncBatchRequest;
import com.digneequipe.hardoize.dto.response.ApiResponse;
import com.digneequipe.hardoize.services.SyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/sync")
@RequiredArgsConstructor
public class SyncController {

    private final SyncService syncService;

    @PostMapping("/batch")
    public ResponseEntity<ApiResponse<Map<String, Object>>> syncBatch(
            @RequestBody SyncBatchRequest request,
            Authentication auth) {
        try {
            Map<String, Object> result = syncService.syncBatch(
                    request, auth.getName());
            return ResponseEntity.ok(ApiResponse.ok("Sync réussie", result));
        } catch (Exception e) {
            return ResponseEntity.ok(
                    ApiResponse.error("Erreur sync: " + e.getMessage()));
        }
    }

    @GetMapping("/check/{uuid}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> check(
            @PathVariable String uuid) {
        return ResponseEntity.ok(
                ApiResponse.ok(Map.of(
                        "uuid",   uuid,
                        "existe", syncService.existsByUuid(uuid)
                ))
        );
    }
}