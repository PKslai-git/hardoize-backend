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

    // Token push Expo (ExponentPushToken[...]), mis à jour à chaque
    // démarrage de l'app côté client. Peut être null (utilisateur pas
    // encore connecté depuis cette mise à jour, ou notifications
    // refusées) — toujours vérifier avant l'envoi.
    private String expoPushToken;

    // Date jusqu'à laquelle l'accès est valide, au-delà du mois
    // d'essai par défaut (createdAt + 30 jours) — null tant que
    // personne ne l'a explicitement prolongée. Sert de terrain
    // préparatoire pour la future gestion d'abonnement payant (même
    // mécanisme : une date d'expiration à comparer à "maintenant"),
    // et permet dès maintenant au développeur de prolonger l'accès
    // d'un utilisateur dont l'essai est arrivé à échéance, pour les
    // besoins des prochains builds de test — voir AccesService et
    // AdminController.
    private java.time.LocalDateTime dateExpirationAcces;
}