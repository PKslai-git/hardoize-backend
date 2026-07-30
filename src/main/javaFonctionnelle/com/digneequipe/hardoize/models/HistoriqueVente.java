package com.digneequipe.hardoize.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "historique_ventes")
@Data @NoArgsConstructor @AllArgsConstructor @SuperBuilder
@EqualsAndHashCode(callSuper = false)
@JsonIgnoreProperties({"hibernateLazyInitializer","handler"})
public class HistoriqueVente extends BaseEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "groupe_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer","handler"})
    private Groupe groupe;

    @Column(nullable = false) private String date;
    @Builder.Default private Double  totalVentes  = 0.0;
    @Builder.Default private Double  totalEspeces = 0.0;
    @Builder.Default private Double  totalCredit  = 0.0;
    @Builder.Default private Double  beneficeNet  = 0.0;
    @Builder.Default private Integer nbVentes     = 0;
}