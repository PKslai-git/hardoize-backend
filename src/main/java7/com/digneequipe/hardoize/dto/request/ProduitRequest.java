package com.digneequipe.hardoize.dto.request;

import jakarta.validation.constraints.*;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProduitRequest {

    @NotBlank(message = "Le nom est obligatoire")
    private String nom;

    private Double prixAchat = 0.0;

    @NotNull(message = "Le prix de vente est obligatoire")
    @Positive
    private Double prixVente;

    private Integer quantiteStock = 0;
    private Integer stockMinimum  = 5;
    private String  categorie;
    private String  photoUri;
    private Long    groupeId;
}