
package com.example.demo.controller;

import com.example.demo.model.Produit;
import com.example.demo.model.QrCodeProcessingResult;
import com.example.demo.model.QrCodeUrl;
import com.example.demo.model.Utilisateur;
import com.example.demo.service.ProduitService;
import com.example.demo.service.QrCodeEncoder;
import com.example.demo.service.CategorieService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;


@Controller
@RequestMapping("/produits")
public class ProduitController {

    private final ProduitService service;
    private final CategorieService categorieService;
    private final QrCodeEncoder qrCodeEncoder;

    public ProduitController(ProduitService service, CategorieService categorieService, QrCodeEncoder qrCodeEncoder) {
        this.service = service;
        this.categorieService = categorieService;
        this.qrCodeEncoder = qrCodeEncoder;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("produits", service.findAll());
        model.addAttribute("produit", new Produit());
        model.addAttribute("categories", categorieService.findAll());
        model.addAttribute("showModal", false);
        return "produits";
    }

    @PostMapping("/acheter/{id}")
    public String acheter(@PathVariable Long id, Model model) {

        Produit p = service.findById(id);

        // 2. Préparer les données du mail
        String emailVendeur = "vendeur@exemple.com";
        if (p.getVendeur() != null) {
            emailVendeur = p.getVendeur().getEmail();
        }

        String sujet = "Achat de : " + p.getNom();
        String corps = "Bonjour, je suis intéressé par votre produit '" + p.getNom() + "' au prix de " + p.getPrix() + "€. Est-il toujours disponible ?";

        // 3. Construire le lien mailto: avec encodage URL pour une compatibilité maximale
        String sujetEncoded = URLEncoder.encode(sujet, StandardCharsets.UTF_8).replace("+", "%20");
        String corpsEncoded = URLEncoder.encode(corps, StandardCharsets.UTF_8).replace("+", "%20");
        String mailto = "mailto:" + emailVendeur.trim() + "?subject=" + sujetEncoded + "&body=" + corpsEncoded;

        QrCodeUrl qrCodeUrl = new QrCodeUrl();
        qrCodeUrl.setUrl(mailto);
        QrCodeProcessingResult result = qrCodeEncoder.generateQrCodeUrl(qrCodeUrl);


        model.addAttribute("produits", service.findAll());
        model.addAttribute("produit", new Produit());
        model.addAttribute("categories", categorieService.findAll());


        model.addAttribute("qrCodeImage", result.getImage());
        model.addAttribute("showModal", true);

        return "produits";
    }

    @PostMapping
    public String save(@ModelAttribute Produit p, HttpSession session) {
        Utilisateur currentUser = (Utilisateur) session.getAttribute("current_user");
        if (currentUser != null) {
            p.setVendeur(currentUser);
        }
        service.save(p);
        return "redirect:/produits";
    }

    @GetMapping("/edit/{id}")
    public String edit(@PathVariable Long id, Model model) {
        model.addAttribute("produits", service.findAll());
        model.addAttribute("produit", service.findById(id)); // On charge le produit à modifier
        model.addAttribute("categories", categorieService.findAll());
        return "produits";
    }

    @GetMapping("/delete/{id}")
    public String delete(@PathVariable Long id) {
        service.delete(id);
        return "redirect:/produits";
    }
}
