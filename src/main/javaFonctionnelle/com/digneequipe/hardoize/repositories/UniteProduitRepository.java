package com.digneequipe.hardoize.repositories;

import com.digneequipe.hardoize.models.UniteProduit;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface UniteProduitRepository
        extends JpaRepository<UniteProduit, Long> {

    Optional<UniteProduit> findByUuid(String uuid);
    boolean existsByUuid(String uuid);
    List<UniteProduit> findByProduitIdOrderByOrdreAsc(Long produitId);
}