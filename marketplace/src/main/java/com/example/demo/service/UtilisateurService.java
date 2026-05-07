package com.example.demo.service;

import com.example.demo.model.Utilisateur;
import com.example.demo.repository.UtilisateurRepository;

import jakarta.mail.MessagingException;

import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import com.example.demo.model.Role;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import javax.naming.AuthenticationException;

@Service
public class UtilisateurService {

	@Autowired
    private UtilisateurRepository repo;

	@Autowired
    private PasswordEncoder passwordEncoder;

	@Autowired
	private OtpService otpService;

	@Autowired
	private EmailService emailService;

    public List<Utilisateur> findAll() {
        return repo.findAll();
    }

    public void save(Utilisateur u) {
        u.setPassword(passwordEncoder.encode(u.getPassword()));

        if (u.getRole() == null) {
            u.setRole(Role.USER);
        }
        repo.save(u);
    }

    public void delete(Long id) {
        repo.deleteById(id);
    }

    public Optional<Utilisateur> findById(Long id) {
        return repo.findById(id);
    }

    public Utilisateur login(String email, String password) throws AuthenticationException {
        Utilisateur u = repo.findByEmail(email);

        if (u == null || !passwordEncoder.matches(password, u.getPassword())) {
			throw new AuthenticationException("Email ou mot de passee incorrecte");
        }
        return u;
    }

    public boolean isAdmin(String email) {
        Utilisateur u = repo.findByEmail(email);
        return u != null && u.getRole() == Role.ADMIN;
    }

    public void sendOtp(Utilisateur user) throws MessagingException {
		String otp = otpService.generateOtp();
		user.setOtp(otp);
		user.setOtpExpiration(LocalDateTime.now().plusMinutes(60));
		this.repo.save(user);

		this.emailService.sendOtp(user.getEmail(), otp);
	}

	public Utilisateur verify(Utilisateur user, String otp) throws IllegalArgumentException {
		user = this.repo.findById(user.getId()).orElseThrow(() -> new IllegalArgumentException("Utilisateur non trouvé"));

		if(!user.getOtp().equals(otp) || LocalDateTime.now().isAfter(user.getOtpExpiration())) {
			throw new IllegalArgumentException("Wrong code or expired. Please try again.");
		}

		user.setEnabled(true);
		user.setOtp(null);
		user.setOtpExpiration(null);

		return this.repo.save(user);
	}
}