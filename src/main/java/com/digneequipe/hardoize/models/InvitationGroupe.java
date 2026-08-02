package com.digneequipe.hardoize.models;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

/**
 * Jeton d'invitation à courte durée de vie, généré à la demande par le
 * propriétaire (bouton "Inviter"). Remplace l'usage du codeQR permanent
 * du groupe pour rejoindre : celui-ci ne change jamais, donc une photo
 * du QR ou un lien partagé restaient valables indéfiniment. Ce jeton-ci
 * expire (voir MINUTES_VALIDITE dans AdhesionService) et ne peut servir
 * qu'UNE seule fois — après quoi une capture ou un lien réutilisé plus
 * tard ne fonctionne plus, il faut en régénérer un.
 */
@Entity
@Table(name = "invitations_groupe")
@Data @NoArgsConstructor @AllArgsConstructor @SuperBuilder
@EqualsAndHashCode(callSuper = false)
public class InvitationGroupe extends BaseEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "groupe_id")
    private Groupe groupe;

    @Column(nullable = false, unique = true)
    private String token;

    @Column(nullable = false)
    private LocalDateTime expireLe;

    @Builder.Default
    private Boolean utilisee = false;

    @Builder.Default
    private Boolean revoquee = false;
}
