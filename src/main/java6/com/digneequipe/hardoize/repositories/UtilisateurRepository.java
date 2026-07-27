// Pattern identique pour tous — exemple GroupeRepository :
package com.digneequipe.hardoize.repositories;

import com.digneequipe.hardoize.models.Utilisateur;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

// UtilisateurRepository
public interface UtilisateurRepository extends JpaRepository<Utilisateur, Long> {
    Optional<Utilisateur> findByUuid(String uuid);
    boolean existsByUuid(String uuid);
    Optional<Utilisateur> findByTelephone(String telephone);
    boolean existsByTelephone(String telephone);
    Optional<Utilisateur> findByEmail(String email);
    boolean existsByEmail(String email);
    Optional<Utilisateur> findById(Long id);
}