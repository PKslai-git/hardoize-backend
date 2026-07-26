// Pattern identique pour tous — exemple GroupeRepository :
package com.digneequipe.hardoize.repositories;

import com.digneequipe.hardoize.models.LigneVente;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

// LigneVenteRepository
public interface LigneVenteRepository extends JpaRepository<LigneVente, Long> {
        Optional<LigneVente> findByUuid(String uuid);
        boolean existsByUuid(String uuid);
        List<LigneVente> findByVenteId(Long venteId);
    }