package com.digneequipe.hardoize.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "historique_paiements")
@Data @NoArgsConstructor @AllArgsConstructor @SuperBuilder
@EqualsAndHashCode(callSuper = false)
@JsonIgnoreProperties({"hibernateLazyInitializer","handler"})
public class HistoriquePaiement extends BaseEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false) private String type;
    @Column(nullable = false) private String sens;
    @Column(nullable = false) private Double montant;
    private String description;
    private String nomClient;
    private String nomFournisseur;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "client_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer","handler"})
    private Client client;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "fournisseur_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer","handler"})
    private Fournisseur fournisseur;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "dette_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer","handler"})
    private Dette dette;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "vente_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer","handler"})
    private Vente vente;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "groupe_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer","handler"})
    private Groupe groupe;
}