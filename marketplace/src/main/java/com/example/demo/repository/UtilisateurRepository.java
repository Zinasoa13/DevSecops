
package com.example.demo.repository;

import com.example.demo.model.Utilisateur;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UtilisateurRepository 
extends JpaRepository<Utilisateur, Long> {

Utilisateur findByNom(String nom);
Utilisateur findByEmail(String email);
}
