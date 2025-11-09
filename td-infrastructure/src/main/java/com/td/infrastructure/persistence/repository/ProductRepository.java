package com.td.infrastructure.persistence.repository;

import com.td.domain.catalog.Product;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Repository
public interface ProductRepository extends BaseRepository<Product> {
    
    List<Product> findByBrandId(UUID brandId);
    
    List<Product> findByNameContainingIgnoreCase(String name);
    
    List<Product> findByRateBetween(BigDecimal minRate, BigDecimal maxRate);

    static Specification<Product> withName(String name) {
        return (root, query, cb) -> 
            name == null ? cb.conjunction() : 
            cb.like(cb.lower(root.get("name")), "%" + name.toLowerCase() + "%");
    }

    static Specification<Product> withDescription(String description) {
        return (root, query, cb) -> 
            description == null ? cb.conjunction() : 
            cb.like(cb.lower(root.get("description")), "%" + description.toLowerCase() + "%");
    }

    static Specification<Product> withBrandId(UUID brandId) {
        return (root, query, cb) -> 
            brandId == null ? cb.conjunction() : 
            cb.equal(root.get("brandId"), brandId);
    }

    static Specification<Product> withRateRange(BigDecimal minRate, BigDecimal maxRate) {
        return (root, query, cb) -> {
            if (minRate == null && maxRate == null) {
                return cb.conjunction();
            } else if (minRate == null) {
                return cb.lessThanOrEqualTo(root.get("rate"), maxRate);
            } else if (maxRate == null) {
                return cb.greaterThanOrEqualTo(root.get("rate"), minRate);
            } else {
                return cb.between(root.get("rate"), minRate, maxRate);
            }
        };
    }

    static Specification<Product> withBrandName(String brandName) {
        return (root, query, cb) -> {
            if (brandName == null) {
                return cb.conjunction();
            }
            return cb.like(cb.lower(root.join("brand").get("name")), 
                         "%" + brandName.toLowerCase() + "%");
        };
    }
}