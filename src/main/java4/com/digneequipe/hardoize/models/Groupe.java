package com.digneequipe.hardoize.models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "groupes")
@Data @NoArgsConstructor @AllArgsConstructor @SuperBuilder
@EqualsAndHashCode(callSuper = false)
@JsonIgnoreProperties({"hibernateLazyInitializer","handler"})
public class Groupe extends BaseEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nom;

    private String description;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "proprietaire_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer","handler"})
    private Utilisateur proprietaire;

    @Column(unique = true)
    private String codeQR;

    private String photoUri;

    @Builder.Default
    private String heureFermeture = "18:00";

    @Builder.Default
    private Boolean estActif = true;

    // MODE : "solo" | "multi"
    @Builder.Default
    private String mode = "solo";
}