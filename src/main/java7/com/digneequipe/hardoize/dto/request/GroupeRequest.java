package com.digneequipe.hardoize.dto.request;

import jakarta.validation.constraints.*;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GroupeRequest {

    @NotBlank(message = "Le nom est obligatoire")
    private String nom;

    private String description;
    private String photoUri;
    private String heureFermeture = "18:00";
}