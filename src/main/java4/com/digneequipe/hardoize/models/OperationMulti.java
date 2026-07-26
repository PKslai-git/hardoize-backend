package com.digneequipe.hardoize.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "operations_multi")
@Data @NoArgsConstructor @AllArgsConstructor @SuperBuilder
@EqualsAndHashCode(callSuper = false)
@JsonIgnoreProperties({"hibernateLazyInitializer","handler"})
public class OperationMulti extends BaseEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Type : "vente" | "mouvement_stock" | "dette" | "client" | "produit"
    @Column(nullable = false)
    private String type;

    // Données de l'opération sérialisées en JSON
    @Column(columnDefinition = "TEXT")
    private String payload;

    // Statut : "en_attente" | "traitee" | "echec"
    @Builder.Default
    private String statut = "en_attente";

    private String messageErreur;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "membre_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer","handler"})
    private MembreGroupe membre;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "groupe_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer","handler"})
    private Groupe groupe;
}