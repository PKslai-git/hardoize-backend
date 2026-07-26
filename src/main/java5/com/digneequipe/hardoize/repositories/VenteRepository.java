// Pattern identique pour tous — exemple GroupeRepository :
package com.digneequipe.hardoize.repositories;

import com.digneequipe.hardoize.models.Vente;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

// VenteRepository
public interface VenteRepository extends JpaRepository<Vente, Long> {
        Optional<Vente> findByUuid(String uuid);
        boolean existsByUuid(String uuid);
        List<Vente> findByGroupeIdOrderByCreatedAtDesc(Long groupeId);
    }