package com.digneequipe.hardoize.repositories;

import com.digneequipe.hardoize.models.HistoriqueVente;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.*;

public interface HistoriqueVenteRepository
        extends JpaRepository<HistoriqueVente, Long> {
    Optional<HistoriqueVente> findByUuid(String uuid);
    boolean existsByUuid(String uuid);
    Optional<HistoriqueVente> findByGroupeIdAndDate(Long groupeId, String date);
    List<HistoriqueVente> findByGroupeIdOrderByDateDesc(Long groupeId);

    // Même pattern que ProduitRepository.findByUuidPourMiseAJour : verrouille
    // la ligne du jour pour tout le groupe le temps de la transaction, pour
    // que deux ventes concurrentes (deux vendeurs différents) incrémentent
    // le même total du jour l'une après l'autre, jamais en écrasant le
    // travail de l'autre (perte d'incrément classique sans ce verrou).
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT h FROM HistoriqueVente h WHERE h.groupe.id = :groupeId AND h.date = :date")
    Optional<HistoriqueVente> findByGroupeIdAndDatePourMiseAJour(
            @Param("groupeId") Long groupeId, @Param("date") String date);
}