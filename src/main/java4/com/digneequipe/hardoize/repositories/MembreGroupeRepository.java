// Pattern identique pour tous — exemple GroupeRepository :
package com.digneequipe.hardoize.repositories;

import com.digneequipe.hardoize.models.MembreGroupe;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

// MembreGroupeRepository
public interface MembreGroupeRepository extends JpaRepository<MembreGroupe, Long> {
        Optional<MembreGroupe> findByUuid(String uuid);
        boolean existsByUuid(String uuid);
        List<MembreGroupe> findByGroupeId(Long groupeId);
        Optional<MembreGroupe> findByGroupeIdAndUtilisateurId(Long groupeId, Long utilisateurId);
        long countByGroupeId(Long groupeId);

        Optional<MembreGroupe> findByGroupeIdAndTelephone(
                Long groupeId, String telephone);
 }