// Pattern identique pour tous — exemple GroupeRepository :
package com.digneequipe.hardoize.repositories;

import com.digneequipe.hardoize.models.Produit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

// ProduitRepository
public interface ProduitRepository extends JpaRepository<Produit, Long> {
        Optional<Produit> findByUuid(String uuid);
        boolean existsByUuid(String uuid);
        List<Produit> findByGroupeId(Long groupeId);
    
        @Modifying
        @Query("UPDATE Produit p SET p.quantiteStock = p.quantiteStock - :qte WHERE p.id = :id AND p.quantiteStock >= :qte")
        int decrementerStock(@Param("id") Long id, @Param("qte") int qte);
    
        @Modifying
        @Query("UPDATE Produit p SET p.quantiteStock = p.quantiteStock + :qte WHERE p.id = :id")
        void incrementerStock(@Param("id") Long id, @Param("qte") int qte);

        List<Produit> findByGroupeIdAndEstActif(Long groupeId, Boolean estActif);
    }