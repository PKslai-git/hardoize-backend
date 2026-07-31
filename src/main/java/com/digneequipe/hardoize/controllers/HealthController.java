package com.digneequipe.hardoize.controllers;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;

@RestController
public class HealthController {

    @GetMapping("/api/health")
    public Map<String, Object> health() {
        return Map.of(
                "status",  "UP",
                "service", "Hardoize Backend",
                "version", "1.0.0"
        );
    }

    @GetMapping("/api/version")
    public Map<String, Object> version() {
        return Map.of(
                "versionMinimale",    "1.0.0",
                "versionRecommandee", "1.0.0",
                "message",
                "Hardoize v1.0.0 — Bienvenue sur votre gestionnaire commercial !"
        );
    }
}