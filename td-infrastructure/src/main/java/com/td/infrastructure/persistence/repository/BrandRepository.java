package com.td.infrastructure.persistence.repository;

import com.td.domain.catalog.Brand;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BrandRepository extends BaseRepository<Brand> {
    
    Optional<Brand> findByNameIgnoreCase(String name);
    
    boolean existsByNameIgnoreCase(String name);

    static Specification<Brand> withName(String name) {
        return (root, query, cb) -> 
            name == null ? cb.conjunction() : 
            cb.like(cb.lower(root.get("name")), "%" + name.toLowerCase() + "%");
    }

    static Specification<Brand> withDescription(String description) {
        return (root, query, cb) -> 
            description == null ? cb.conjunction() : 
            cb.like(cb.lower(root.get("description")), "%" + description.toLowerCase() + "%");
    }
}