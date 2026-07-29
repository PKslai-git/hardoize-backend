package com.digneequipe.hardoize.dto.response;

import lombok.*;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UtilisateurResponse {

    private Long          id;
    private String        nom;
    private String        telephone;
    private String        email;
    private String        role;
    private String        photoUri;
    private Float         evaluation;
    private Boolean       estActif;
    private LocalDateTime createdAt;
}