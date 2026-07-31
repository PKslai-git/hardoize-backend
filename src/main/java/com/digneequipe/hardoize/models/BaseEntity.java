package com.digneequipe.hardoize.models;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

@MappedSuperclass
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder(toBuilder = true)
public abstract class BaseEntity {

    // UUID généré côté frontend — clé de déduplication
    @Column(unique = true, nullable = false, updatable = false)
    private String uuid;

    @Column(updatable = false)
    private LocalDateTime createdAt;

    @Column
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (uuid == null) uuid = java.util.UUID.randomUUID().toString();
        // IMPORTANT : LocalDateTime n'a pas de fuseau horaire. On force
        // explicitement UTC ici (au lieu de LocalDateTime.now(), qui
        // dépend du fuseau par défaut de la JVM/du serveur) pour que la
        // valeur stockée représente TOUJOURS l'heure UTC, quel que soit
        // l'hébergeur. Le client (app mobile) doit symétriquement
        // interpréter cette valeur comme de l'UTC (voir parseServerDate
        // côté frontend) — sans ce contrat des deux côtés, un appareil
        // dans un fuseau ≠ UTC voit toutes les dates décalées, ce qui
        // fausse le tri chronologique des ventes/mouvements/dettes.
        createdAt = LocalDateTime.now(ZoneOffset.UTC);
        updatedAt = LocalDateTime.now(ZoneOffset.UTC);
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now(ZoneOffset.UTC);
    }
}