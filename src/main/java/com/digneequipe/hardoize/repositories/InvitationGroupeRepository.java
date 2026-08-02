package com.digneequipe.hardoize.repositories;

import com.digneequipe.hardoize.models.InvitationGroupe;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface InvitationGroupeRepository extends JpaRepository<InvitationGroupe, Long> {
    Optional<InvitationGroupe> findByToken(String token);
}
