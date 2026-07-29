package com.digneequipe.hardoize.dto.response;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StatsResponse {

    // Ventes
    private Double totalVentes;
    private Double totalEspeces;
    private Double totalCredit;
    private Long   nbVentes;

    // Finances
    private Double beneficeNet;
    private Double totalDettesClients;
    private Double totalDettessFournisseurs;

    // Score
    private Double scoreMoyenClients;
    private Long   nbRetards;

    // Période
    private String periode;
}