package com.digneequipe.hardoize.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "permissions_membres")
@Data @NoArgsConstructor @AllArgsConstructor @SuperBuilder
@EqualsAndHashCode(callSuper = false)
@JsonIgnoreProperties({"hibernateLazyInitializer","handler"})
public class PermissionMembre extends BaseEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "membre_id", unique = true)
    @JsonIgnoreProperties({"hibernateLazyInitializer","handler"})
    private MembreGroupe membre;

    @Builder.Default private Boolean peutVendre         = true;
    @Builder.Default private Boolean peutVoirDettes     = false;
    @Builder.Default private Boolean peutGererStock     = false;
    @Builder.Default private Boolean peutVoirStats      = false;
    @Builder.Default private Boolean peutGererClients   = false;
    @Builder.Default private Boolean peutVoirHistorique = false;
}