package com.digneequipe.hardoize.dto.request;

import lombok.Data;
import java.util.*;

@Data
public class SyncBatchRequest {
    private List<Map<String,Object>> groupes;
    private List<Map<String,Object>> membres;
    private List<Map<String,Object>> fournisseurs;
    private List<Map<String,Object>> unitesProduit;
    private List<Map<String,Object>> produits;
    private List<Map<String,Object>> clients;
    private List<Map<String,Object>> ventes;
    private List<Map<String,Object>> lignesVentes;
    private List<Map<String,Object>> dettes;
    private List<Map<String,Object>> dettesFournisseurs;
    private List<Map<String,Object>> mouvementsStock;
    private List<Map<String,Object>> historiqueVentes;
    private List<Map<String,Object>> historiquePaiements;
}