package com.digneequipe.hardoize.repositories;

import com.digneequipe.hardoize.models.OperationMulti;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface OperationMultiRepository
        extends JpaRepository<OperationMulti, Long> {
    List<OperationMulti> findByGroupeIdAndStatutOrderByCreatedAtAsc(
            Long groupeId, String statut
    );
    List<OperationMulti> findByGroupeIdOrderByCreatedAtDesc(Long groupeId);
    long countByGroupeIdAndStatut(Long groupeId, String statut);
}