// Pattern identique pour tous — exemple GroupeRepository :
package com.digneequipe.hardoize.repositories;

import com.digneequipe.hardoize.models.Groupe;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GroupeRepository extends JpaRepository<Groupe, Long> {
    Optional<Groupe> findByUuid(String uuid);
    boolean existsByUuid(String uuid);
    Optional<Groupe> findByCodeQR(String codeQR);

    List<Groupe> findByProprietaireId(Long proprietaireId);
}