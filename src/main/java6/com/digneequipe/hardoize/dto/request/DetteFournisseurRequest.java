package com.digneequipe.hardoize.dto.request;

import jakarta.validation.constraints.*;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DetteFournisseurRequest {

    @NotNull
    private Long fournisseurId;

    @NotNull @Positive
    private Double montantTotal;

    @NotBlank
    private String dateRemboursement;

    private String motif;
    private Long   groupeId;
}