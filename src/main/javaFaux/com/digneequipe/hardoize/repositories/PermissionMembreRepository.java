// Pattern identique pour tous — exemple GroupeRepository :
package com.digneequipe.hardoize.repositories;

import com.digneequipe.hardoize.models.PermissionMembre;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

// PermissionMembreRepository
public interface PermissionMembreRepository extends JpaRepository<PermissionMembre, Long> {
        Optional<PermissionMembre> findByUuid(String uuid);
        boolean existsByUuid(String uuid);
        Optional<PermissionMembre> findByMembreId(Long membreId);
    }