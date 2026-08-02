package com.digneequipe.hardoize.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "lignes_ventes")
@Data @NoArgsConstructor @AllArgsConstructor @SuperBuilder
@EqualsAndHashCode(callSuper = false)
@JsonIgnoreProperties({"hibernateLazyInitializer","handler"})
public class LigneVente extends BaseEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vente_id", nullable = false)
    @JsonIgnoreProperties({"lignes","hibernateLazyInitializer","handler"})
    private Vente vente;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "produit_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer","handler"})
    private Produit produit;

    @Column(nullable = false) private String  nomProduit;
    @Column(nullable = false) private Integer quantite;
    @Builder.Default private Double prixAchat    = 0.0;
    @Builder.Default private Double prixUnitaire = 0.0;
    @Builder.Default private Double sousTotal    = 0.0;
    @Builder.Default private Double marge        = 0.0;

    @Builder.Default
    private String  uniteNom     = "pcs";
    @Builder.Default
    private Double  uniteFacteur = 1.0;
    @Builder.Default
    private Boolean prixModifie  = false;
}