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

    // Résultat de l'opération une fois traitée (JSON), utilisé pour
    // renvoyer exactement la même réponse en cas de nouvelle tentative
    // du client avec le même uuid d'opération (idempotence) — évite de
    // ré-appliquer deux fois une vente/remboursement/mouvement de stock
    // si la réponse s'est perdue en réseau après que le serveur a déjà
    // traité l'opération avec succès.
    @Column(columnDefinition = "TEXT")
    private String resultat;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "membre_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer","handler"})
    private MembreGroupe membre;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "groupe_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer","handler"})
    private Groupe groupe;
}