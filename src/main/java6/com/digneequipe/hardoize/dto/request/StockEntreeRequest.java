package com.digneequipe.hardoize.dto.request;

import jakarta.validation.constraints.*;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StockEntreeRequest {

    @NotNull @Positive
    private Integer quantite;

    private Double prixAchat = 0.0;
    private Long   fournisseurId;
    private String motif = "achat";
}