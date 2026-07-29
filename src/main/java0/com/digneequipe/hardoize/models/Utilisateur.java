package com.digneequipe.hardoize.models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "utilisateurs")
@Data @NoArgsConstructor @AllArgsConstructor @SuperBuilder
@EqualsAndHashCode(callSuper = false)
@JsonIgnoreProperties({"hibernateLazyInitializer","handler"})
public class Utilisateur extends BaseEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nom;

    @Column(nullable = false, unique = true)
    private String telephone;

    private String email;

    @Builder.Default
    private Double evaluation = 5.0;

    @JsonIgnore
    private String motDePasse;

    @Builder.Default
    private String role = "vendeur";

    private String photoUri;

    @Builder.Default
    private Boolean estActif = true;
}