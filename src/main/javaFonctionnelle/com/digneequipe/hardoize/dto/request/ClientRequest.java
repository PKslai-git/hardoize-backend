package com.digneequipe.hardoize.dto.request;

import jakarta.validation.constraints.*;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ClientRequest {

    @NotBlank(message = "Le nom est obligatoire")
    private String nomClient;

    @NotBlank(message = "Le numéro est obligatoire")
    private String numeroClient;

    private String email;
    private String photoUri;
    private Long   groupeId;
}