package com.td.domain.catalog;

import com.td.domain.common.contracts.AuditableEntity;
import com.td.domain.common.contracts.IAggregateRoot;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "products")
@Getter
@Setter(AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Product extends AuditableEntity<UUID> implements IAggregateRoot {

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal rate;

    @Column(name = "image_path")
    private String imagePath;

    @Column(name = "brand_id", nullable = false)
    private UUID brandId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "brand_id", insertable = false, updatable = false)
    private Brand brand;

    public Product(String name, String description, BigDecimal rate, UUID brandId, String imagePath) {
        this.name = name;
        this.description = description;
        this.rate = rate;
        this.brandId = brandId;
        this.imagePath = imagePath;
    }

    public Product update(String name, String description, BigDecimal rate, UUID brandId, String imagePath) {
        if (name != null && !this.name.equals(name)) {
            this.name = name;
        }
        if (description != null && !description.equals(this.description)) {
            this.description = description;
        }
        if (rate != null && this.rate.compareTo(rate) != 0) {
            this.rate = rate;
        }
        if (brandId != null && !brandId.equals(UUID.fromString("00000000-0000-0000-0000-000000000000")) 
            && !this.brandId.equals(brandId)) {
            this.brandId = brandId;
        }
        if (imagePath != null && !imagePath.equals(this.imagePath)) {
            this.imagePath = imagePath;
        }
        return this;
    }

    public Product clearImagePath() {
        this.imagePath = "";
        return this;
    }

    // Business methods
    public boolean isExpensive() {
        return rate.compareTo(BigDecimal.valueOf(1000)) > 0;
    }

    public Product applyDiscount(BigDecimal discountPercentage) {
        if (discountPercentage.compareTo(BigDecimal.ZERO) > 0 && 
            discountPercentage.compareTo(BigDecimal.valueOf(100)) <= 0) {
            BigDecimal discountAmount = rate.multiply(discountPercentage).divide(BigDecimal.valueOf(100));
            this.rate = rate.subtract(discountAmount);
        }
        return this;
    }
}