package com.example.app;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PeanutsService {

    private final PeanutsRepository repository;

    public PeanutsService(PeanutsRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "peanuts", key = "#id")
    public Peanuts getPeanutsById(Long id) {
        return repository.findById(id).orElse(null);
    }

    @Transactional
    @CachePut(value = "peanuts", key = "#peanuts.id")
    public Peanuts savePeanuts(Peanuts peanuts) {
        return repository.save(peanuts);
    }
}
