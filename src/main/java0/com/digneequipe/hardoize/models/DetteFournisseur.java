package com.digneequipe.hardoize.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

@Entity
@Table(name = "dettes_fournisseurs")
@Data @NoArgsConstructor @AllArgsConstructor @SuperBuilder
@EqualsAndHashCode(callSuper = false)
@JsonIgnoreProperties({"hibernateLazyInitializer","handler"})
public class DetteFournisseur extends BaseEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fournisseur_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer","handler"})
    private Fournisseur fournisseur;

    private String  nomFournisseur;

    @Column(nullable = false)
    private Double montantTotal;
    @Builder.Default private Double montantRembourse = 0.0;
    @Builder.Default private Double montantRestant   = 0.0;

    private LocalDateTime dateRemboursement;
    private String motif;
    private String lignesJson;
    private String paiementsJson;

    @Builder.Default private String statut = "active";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "groupe_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer","handler"})
    private Groupe groupe;

    @PrePersist
    @Override
    protected void onCreate() {
        super.onCreate();
        if (montantRestant == null || montantRestant == 0.0)
            montantRestant = montantTotal;
    }
}