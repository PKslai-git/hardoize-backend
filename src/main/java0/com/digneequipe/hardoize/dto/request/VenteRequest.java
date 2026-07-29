package com.digneequipe.hardoize.dto.request;

import lombok.Data;
import java.util.List;

@Data
public class VenteRequest {

    private List<LigneVenteRequest> lignes; // panier multi-produits
    private String   typePaiement;   // "especes" | "credit" | "mixte"
    private Long     clientId;
    private Long     groupeId;
    private String   dateRemboursement; // si credit, format JJ/MM/AAAA
    private Double   montantEspeces;    // si mixte

    @Data
    public static class LigneVenteRequest {
        private Long    produitId;
        private String  nomProduit;
        private Integer quantite;
        private Double  prixUnitaire;
        private Double  prixAchat;
    }
}