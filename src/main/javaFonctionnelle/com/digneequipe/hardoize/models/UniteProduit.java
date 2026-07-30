package com.digneequipe.hardoize.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "unites_produit")
@Data @NoArgsConstructor @AllArgsConstructor @SuperBuilder
@EqualsAndHashCode(callSuper = false)
@JsonIgnoreProperties({"hibernateLazyInitializer","handler"})
public class UniteProduit extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "produit_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer","handler"})
    private Produit produit;

    @Column(nullable = false)
    private String nom;

    @Builder.Default
    private Double  facteur   = 1.0;

    @Builder.Default
    private Double  prixAchat = 0.0;

    @Builder.Default
    private Double  prixVente = 0.0;

    @Builder.Default
    private Boolean estBase   = false;

    @Builder.Default
    private Integer ordre     = 0;

    @Builder.Default
    private Boolean estReference = false;
}