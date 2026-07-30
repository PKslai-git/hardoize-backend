package com.digneequipe.hardoize.repositories;

import com.digneequipe.hardoize.models.HistoriqueVente;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface HistoriqueVenteRepository
        extends JpaRepository<HistoriqueVente, Long> {
    Optional<HistoriqueVente> findByUuid(String uuid);
    boolean existsByUuid(String uuid);
    Optional<HistoriqueVente> findByGroupeIdAndDate(Long groupeId, String date);
    List<HistoriqueVente> findByGroupeIdOrderByDateDesc(Long groupeId);
}