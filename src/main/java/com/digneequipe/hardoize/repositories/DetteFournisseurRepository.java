// Pattern identique pour tous — exemple GroupeRepository :
package com.digneequipe.hardoize.repositories;

import com.digneequipe.hardoize.models.DetteFournisseur;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

// DetteFournisseurRepository
public interface DetteFournisseurRepository extends JpaRepository<DetteFournisseur, Long> {
        Optional<DetteFournisseur> findByUuid(String uuid);
        boolean existsByUuid(String uuid);
        List<DetteFournisseur> findByGroupeId(Long groupeId);
    }