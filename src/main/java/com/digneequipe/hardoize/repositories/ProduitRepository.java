// Pattern identique pour tous — exemple GroupeRepository :
package com.digneequipe.hardoize.repositories;

import com.digneequipe.hardoize.models.Produit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

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

        // ── Exclusion mutuelle à attente active ─────────────────────
        // SELECT ... FOR UPDATE : verrouille la ligne en base pour
        // toute la durée de la transaction appelante. Si une deuxième
        // vente/mouvement concurrent porte sur le MÊME produit, sa
        // transaction reste bloquée (attente active côté base de
        // données) jusqu'à ce que la première commit ou annule — elle
        // reprend alors avec la valeur de stock FRAÎCHE, jamais une
        // copie obsolète. Les opérations sur des produits différents
        // ne se bloquent pas entre elles (verrou par ligne, pas par
        // table). Remplace l'ancienne combinaison UPDATE atomique +
        // relecture, qui pouvait renvoyer une valeur mise en cache par
        // Hibernate (persistence context) au lieu de la vraie valeur
        // en base après l'UPDATE.
        @Lock(LockModeType.PESSIMISTIC_WRITE)
        @Query("SELECT p FROM Produit p WHERE p.id = :id")
        Optional<Produit> findByIdPourMiseAJour(@Param("id") Long id);

        @Lock(LockModeType.PESSIMISTIC_WRITE)
        @Query("SELECT p FROM Produit p WHERE p.uuid = :uuid")
        Optional<Produit> findByUuidPourMiseAJour(@Param("uuid") String uuid);

        List<Produit> findByGroupeIdAndEstActif(Long groupeId, Boolean estActif);
    }