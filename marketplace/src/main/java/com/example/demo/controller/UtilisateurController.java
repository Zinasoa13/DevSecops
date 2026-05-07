package com.example.demo.controller;

import com.example.demo.model.Role;
import com.example.demo.model.Utilisateur;
import com.example.demo.service.UtilisateurService;

import jakarta.servlet.http.HttpSession;


import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
public class UtilisateurController {

    private final UtilisateurService service;

    public UtilisateurController(UtilisateurService service) {
        this.service = service;
    }

    @GetMapping("/")
    public String home() {
        return "redirect:/login";
    }

    @GetMapping("/utilisateurs")
    public String list(Model model, HttpSession session) {
    	Utilisateur user = (Utilisateur) session.getAttribute("current_user");
    	if(user == null) {
    		return "redirect:/login";
    	}
    	if(!user.getRole().equals(Role.ADMIN )) {
    		return "redirect:/produits";
    	}
        model.addAttribute("utilisateurs", service.findAll());
        model.addAttribute("utilisateur", new Utilisateur());
        return "utilisateurs";
    }

    @PostMapping("/utilisateurs")
    public String save(@ModelAttribute("utilisateur") Utilisateur u, HttpSession session) {
    	Utilisateur user = (Utilisateur) session.getAttribute("current_user");
    	if(user == null) {
    		return "redirect:/login";
    	}
        service.save(u);
        return "redirect:/utilisateurs";
    }

    @GetMapping("/login")
    public String loginForm(HttpSession session) {
    	Utilisateur user = (Utilisateur) session.getAttribute("current_user");
    	if(user != null) {
    		if(user.getRole().equals(Role.ADMIN )) {
        		return "redirect:/utilisateurs";
    		} else {
        		return "redirect:/produits";
    		}
    	}
        return "login";
    }

    @PostMapping("/login")
    public String login(@RequestParam String email,
                        @RequestParam String password,
						HttpSession session,
                        Model model) {

    	Utilisateur user = (Utilisateur) session.getAttribute("current_user");
    	if(user != null) {
    		if(user.getRole().equals(Role.ADMIN )) {
        		return "redirect:/utilisateurs";
    		} else {
        		return "redirect:/produits";
    		}
    	}
		try {
			user = service.login(email, password);
			service.sendOtp(user);
		} catch(Exception e) {
			model.addAttribute("error", e.getMessage());
			return "login";
		}

		session.setAttribute("temp_user", user);
		return "redirect:/verify-otp";
    }

	@GetMapping("/verify-otp")
	public String verifyForm(HttpSession session) {
    	Utilisateur user = (Utilisateur) session.getAttribute("current_user");
    	if(user != null) {
    		if(user.getRole().equals(Role.ADMIN )) {
        		return "redirect:/utilisateurs";
    		} else {
        		return "redirect:/produits";
    		}
    	}
		return "verify-otp";
	}

	@PostMapping("/verify-otp")
	public String verify(@RequestParam String otp, HttpSession session, Model model) {

    	Utilisateur user = (Utilisateur) session.getAttribute("current_user");
    	if(user != null) {
    		if(user.getRole().equals(Role.ADMIN )) {
        		return "redirect:/utilisateurs";
    		} else {
        		return "redirect:/produits";
    		}
    	}
    	user = (Utilisateur) session.getAttribute("temp_user");
		try {
			user = this.service.verify(user, otp);
		} catch(Exception e) {
			model.addAttribute("error", e.getMessage());
			return "verify-otp";
		}

		session.removeAttribute("temp_user");
		session.setAttribute("current_user", user);

		if(user.getRole().equals(Role.ADMIN)) {
			return "redirect:/utilisateurs";
		} else {
			return "redirect:/produits";
		}
	}

    @GetMapping("/utilisateurs/delete/{id}")
    public String delete(@PathVariable Long id) {
        service.delete(id);
        return "redirect:/utilisateurs";
    }

    @GetMapping("/logout")
    public String logOut(HttpSession session) {
    	session.removeAttribute("current_user");
    	return "login";
    }
}