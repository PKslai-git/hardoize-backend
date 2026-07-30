package com.digneequipe.hardoize.security;

import com.digneequipe.hardoize.models.Utilisateur;
import com.digneequipe.hardoize.repositories.UtilisateurRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UtilisateurRepository utilisateurRepository;

    @Override
    public UserDetails loadUserByUsername(String telephone)
            throws UsernameNotFoundException {

        Utilisateur utilisateur = utilisateurRepository
                .findByTelephone(telephone)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "Utilisateur non trouvé : " + telephone
                ));

        return new User(
                utilisateur.getTelephone(),
                utilisateur.getMotDePasse(),
                List.of(new SimpleGrantedAuthority("ROLE_" + utilisateur.getRole().toUpperCase()))
        );
    }
}