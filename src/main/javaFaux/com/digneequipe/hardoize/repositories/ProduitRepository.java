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
    
        // clearAutomatically = true : sans ça, un findByUuid/findById sur
        // le MÊME produit plus loin dans la même transaction renverrait
        // encore l'entité mise en cache par le contexte de persistance
        // AVANT cette mise à jour en masse (JPQL bulk update), donc un
        // stock périmé — critique ici car le mode multi doit renvoyer au
        // frontend le stock réellement à jour juste après l'avoir modifié.
        @Modifying(clearAutomatically = true)
        @Query("UPDATE Produit p SET p.quantiteStock = p.quantiteStock - :qte WHERE p.id = :id AND p.quantiteStock >= :qte")
        int decrementerStock(@Param("id") Long id, @Param("qte") int qte);
    
        @Modifying(clearAutomatically = true)
        @Query("UPDATE Produit p SET p.quantiteStock = p.quantiteStock + :qte WHERE p.id = :id")
        void incrementerStock(@Param("id") Long id, @Param("qte") int qte);

        List<Produit> findByGroupeIdAndEstActif(Long groupeId, Boolean estActif);
    }