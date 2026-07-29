// Pattern identique pour tous — exemple GroupeRepository :
package com.digneequipe.hardoize.repositories;

import com.digneequipe.hardoize.models.Fournisseur;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

// FournisseurRepository
public interface FournisseurRepository extends JpaRepository<Fournisseur, Long> {
        Optional<Fournisseur> findByUuid(String uuid);
        boolean existsByUuid(String uuid);
        List<Fournisseur> findByGroupeId(Long groupeId);

    List<Fournisseur> findByGroupeIdAndEstActif(Long groupeId, Boolean estActif);
    }