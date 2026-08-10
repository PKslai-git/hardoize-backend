package com.digneequipe.hardoize.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "produits")
@Data @NoArgsConstructor @AllArgsConstructor @SuperBuilder
@EqualsAndHashCode(callSuper = false)
@JsonIgnoreProperties({"hibernateLazyInitializer","handler"})
public class Produit extends BaseEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nom;

    private String categorie;

    // Code-barres du produit (EAN-13, UPC, Code128...) — saisi
    // manuellement ou scanné à la caméra. Optionnel, mais quand présent
    // doit être unique par groupe (contrainte gérée au niveau service,
    // pas en base, pour rester tolérant aux anciens produits sans
    // code-barres qui partagent tous une valeur nulle).
    private String codeBarre;

    @Builder.Default private Double  prixAchat     = 0.0;
    @Column(nullable = false)
    private Double  prixVente;
    @Builder.Default private Integer quantiteStock = 0;
    @Builder.Default private Integer stockMinimum  = 5;

    private String photoUri;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fournisseur_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer","handler"})
    private Fournisseur fournisseur;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "groupe_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer","handler"})
    private Groupe groupe;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "utilisateur_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer","handler"})
    private Utilisateur utilisateur;

    @Builder.Default private Boolean estActif = true;
}