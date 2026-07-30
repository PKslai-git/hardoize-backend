package com.digneequipe.hardoize.dto.request;

import jakarta.validation.constraints.*;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MembreRequest {

    @NotBlank
    private String nomAffiche;

    private String telephone;
    private String role       = "vendeur";
    private String bailHeure  = "18:00";
    private Long   utilisateurId;
    private Boolean connexionPermanente = false;
}