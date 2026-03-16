package com.td.application.catalog.brands;

import com.td.application.common.interfaces.IRepository;
import com.td.domain.catalog.Brand;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.util.Optional;

public interface BrandRepository extends IRepository<Brand> {

    Optional<Brand> findByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCase(String name);

    Page<Brand> findAll(Specification<Brand> spec, Pageable pageable);

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