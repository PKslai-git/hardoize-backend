package com.digneequipe.hardoize.repositories;

import com.digneequipe.hardoize.models.HistoriquePaiement;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface HistoriquePaiementRepository
        extends JpaRepository<HistoriquePaiement, Long> {
    Optional<HistoriquePaiement> findByUuid(String uuid);
    boolean existsByUuid(String uuid);
    List<HistoriquePaiement> findByGroupeIdOrderByCreatedAtDesc(Long groupeId);
}