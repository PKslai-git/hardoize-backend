package com.digneequipe.hardoize.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "membres_groupe")
@Data @NoArgsConstructor @AllArgsConstructor @SuperBuilder
@EqualsAndHashCode(callSuper = false)
@JsonIgnoreProperties({"hibernateLazyInitializer","handler"})
public class MembreGroupe extends BaseEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "groupe_id")
    @JsonIgnoreProperties({"membres","hibernateLazyInitializer","handler"})
    private Groupe groupe;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "utilisateur_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer","handler"})
    private Utilisateur utilisateur;

    private String nomAffiche;
    private String telephone;

    @Builder.Default private String  role               = "vendeur";
    @Builder.Default private String  bailHeure          = "18:00";
    @Builder.Default private Boolean estConnecte        = false;
    @Builder.Default private Boolean estActif           = true;
    @Builder.Default private Boolean connexionPermanente = false;

    // Horodatage du dernier "heartbeat" (appel /multi/connecter, envoyé
    // à chaque poll 30s côté app). Sans ça, estConnecte restait bloqué
    // à `true` indéfiniment dès la première connexion — même après
    // fermeture de l'app sans passer par le bail programmé — car rien
    // ne permettait de détecter qu'un membre avait simplement cessé de
    // pinguer. Le statut affiché doit maintenant se baser sur la
    // fraîcheur de ce champ, pas uniquement sur le booléen brut.
    private java.time.LocalDateTime derniereActivite;
}