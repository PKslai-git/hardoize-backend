package com.digneequipe.hardoize.dto.request;

import jakarta.validation.constraints.*;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DetteRequest {

    @NotNull
    private Long clientId;

    private Long venteId;

    @NotNull @Positive
    private Double montantTotal;

    @NotBlank
    private String dateRemboursement;

    private Long groupeId;
}