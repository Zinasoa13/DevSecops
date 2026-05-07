package com.example.demo.model;

import java.time.LocalDateTime;

import jakarta.persistence.*;

@Entity
public class Utilisateur {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nom;
    @Column(unique = true)
    private String email;
    private String password;

    @Enumerated(EnumType.STRING) // Enregistre "ADMIN" ou "USER" en base
    private Role role;

    
    private String otp;
    private LocalDateTime otpExpiration;
    private boolean enabled = false;

    // Constructeur par défaut
    public Utilisateur() {}
    
    public Utilisateur(String nom, String email, String password, Role role) {
  		this.nom = nom;
  		this.email = email;
  		this.password = password;
  		this.role = role;
  	}

    // Getters et Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getNom() {
        return nom;
    }

    public void setNom(String nom) {
        this.nom = nom;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

	public String getOtp() {
		return otp;
	}

	public void setOtp(String otp) {
		this.otp = otp;
	}

	public LocalDateTime getOtpExpiration() {
		return otpExpiration;
	}

	public void setOtpExpiration(LocalDateTime otpExpiration) {
		this.otpExpiration = otpExpiration;
	}

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}
    
    
}