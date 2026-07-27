// Pattern identique pour tous — exemple GroupeRepository :
package com.digneequipe.hardoize.repositories;

import com.digneequipe.hardoize.models.Client;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

// ClientRepository
public interface ClientRepository extends JpaRepository<Client, Long> {
        Optional<Client> findByUuid(String uuid);
        boolean existsByUuid(String uuid);
        List<Client> findByGroupeId(Long groupeId);

    List<Client> findByGroupeIdAndEstActif(Long groupeId, Boolean estActif);
    }