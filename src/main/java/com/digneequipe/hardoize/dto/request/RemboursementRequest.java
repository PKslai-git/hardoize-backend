package com.digneequipe.hardoize.dto.request;

import jakarta.validation.constraints.*;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RemboursementRequest {

    @NotNull @Positive
    private Double montant;
}