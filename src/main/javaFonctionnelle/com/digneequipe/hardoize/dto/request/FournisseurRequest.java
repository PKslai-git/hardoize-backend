package com.digneequipe.hardoize.dto.request;

import jakarta.validation.constraints.*;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FournisseurRequest {

    @NotBlank(message = "Le nom est obligatoire")
    private String nom;

    private String telephone;
    private String email;
    private String adresse;
    private String photoUri;
    private Long   groupeId;
}