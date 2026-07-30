// Pattern identique pour tous — exemple GroupeRepository :
package com.digneequipe.hardoize.repositories;

import com.digneequipe.hardoize.models.Dette;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

// DetteRepository
public interface DetteRepository extends JpaRepository<Dette, Long> {
        Optional<Dette> findByUuid(String uuid);
        boolean existsByUuid(String uuid);
        List<Dette> findByGroupeId(Long groupeId);
        List<Dette> findByClientId(Long clientId);
    }