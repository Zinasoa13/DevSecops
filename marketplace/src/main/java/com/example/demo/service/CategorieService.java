
package com.example.demo.service;

import com.example.demo.model.Categorie;
import com.example.demo.repository.CategorieRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CategorieService {
    private final CategorieRepository repo;

    public CategorieService(CategorieRepository repo) {
        this.repo = repo;
    }

    public List<Categorie> findAll() {
        return repo.findAll();
    }

    public void save(Categorie c) {
        repo.save(c);
    }

    public void delete(Long id) {
        repo.deleteById(id);
    }
}
