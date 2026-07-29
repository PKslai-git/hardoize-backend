package com.digneequipe.hardoize.dto.request;

import lombok.Data;

@Data
public class AuthRequest {
    private String nom;
    private String telephone;
    private String motDePasse;
    private String role;
}