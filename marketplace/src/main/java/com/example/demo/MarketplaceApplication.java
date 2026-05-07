package com.example.demo;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.example.demo.model.Role;
import com.example.demo.model.Utilisateur;
import com.example.demo.repository.UtilisateurRepository;

@SpringBootApplication
public class MarketplaceApplication {

	public static void main(String[] args) {
		SpringApplication.run(MarketplaceApplication.class, args);
	}

	@Bean
    CommandLineRunner start(UtilisateurRepository repo, PasswordEncoder encoder) {
		String nom = "Administrateur";
		String mail = "fideranaandria13@gmail.com";
		String password = "123";
        return args -> {
            // ON VÉRIFIE SI L'ADMIN EXISTE DÉJÀ POUR NE PAS LE CRÉER 100 FOIS
            if (repo.findByEmail(mail) == null) {
                Utilisateur admin = new Utilisateur();
                admin.setNom(nom);
                admin.setEmail(mail);
                admin.setPassword(encoder.encode(password));
                admin.setRole(Role.ADMIN);
                repo.save(admin);
                System.out.println(">>> COMPTE ADMIN CRÉÉ AVEC SUCCÈS (mdp: " + password + ") <<<");
            }
        };
    }

}
