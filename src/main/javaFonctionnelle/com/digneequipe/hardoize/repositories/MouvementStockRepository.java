// Pattern identique pour tous — exemple GroupeRepository :
package com.digneequipe.hardoize.repositories;

import com.digneequipe.hardoize.models.MouvementStock;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

// MouvementStockRepository
public interface MouvementStockRepository extends JpaRepository<MouvementStock, Long> {
        Optional<MouvementStock> findByUuid(String uuid);
        boolean existsByUuid(String uuid);
        List<MouvementStock> findByGroupeIdOrderByCreatedAtDesc(Long groupeId);
    }