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

/**
 * Entity Sản phẩm - Quản lý thông tin sản phẩm trong hệ thống catalog
 * 
 * Domain Aggregate Root cho việc quản lý sản phẩm
 * Kế thừa từ AuditableEntity để tự động tracking thông tin audit (created, modified, deleted)
 * 
 * Sử dụng:
 * - Tạo sản phẩm mới: new Product(name, description, rate, brandId, imagePath)
 * - Cập nhật: product.update(newName, newDescription, newRate, newBrandId, newImagePath)
 * - Xóa ảnh: product.clearImagePath()
 * - Giảm giá: product.applyDiscount(BigDecimal.valueOf(10)) // Giảm 10%
 */
@Entity
@Table(name = "products")
@Getter
@Setter(AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Product extends AuditableEntity<UUID> implements IAggregateRoot {

    // Tên sản phẩm (bắt buộc)
    @Column(nullable = false)
    private String name;

    // Mô tả chi tiết sản phẩm
    @Column(columnDefinition = "TEXT")
    private String description;

    // Giá sản phẩm (18 chữ số, 2 chữ số thập phân)
    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal rate;

    // Đường dẫn hình ảnh sản phẩm
    @Column(name = "image_path")
    private String imagePath;

    // ID thương hiệu (foreign key)
    @Column(name = "brand_id", nullable = false)
    private UUID brandId;

    // Navigation property - Thương hiệu của sản phẩm (lazy loading)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "brand_id", insertable = false, updatable = false)
    private Brand brand;

    /**
     * Constructor - Tạo sản phẩm mới
     * 
     * @param name Tên sản phẩm (bắt buộc)
     * @param description Mô tả sản phẩm
     * @param rate Giá sản phẩm (bắt buộc)
     * @param brandId ID thương hiệu (bắt buộc)
     * @param imagePath Đường dẫn ảnh sản phẩm
     */
    public Product(String name, String description, BigDecimal rate, UUID brandId, String imagePath) {
        this.name = name;
        this.description = description;
        this.rate = rate;
        this.brandId = brandId;
        this.imagePath = imagePath;
    }

    /**
     * Cập nhật thông tin sản phẩm
     * Chỉ cập nhật các trường có giá trị mới khác null và khác giá trị hiện tại
     * 
     * @param name Tên mới (null = không thay đổi)
     * @param description Mô tả mới (null = không thay đổi)
     * @param rate Giá mới (null = không thay đổi)
     * @param brandId Thương hiệu mới (null hoặc empty UUID = không thay đổi)
     * @param imagePath Đường dẫn ảnh mới (null = không thay đổi)
     * @return Đối tượng Product sau khi cập nhật (cho phép method chaining)
     */
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

    /**
     * Xóa đường dẫn ảnh sản phẩm (đặt về rỗng)
     * 
     * @return Đối tượng Product sau khi xóa ảnh
     */
    public Product clearImagePath() {
        this.imagePath = "";
        return this;
    }

    // === Business Methods - Các phương thức nghiệp vụ ===

    /**
     * Kiểm tra sản phẩm có giá cao không (> 1000)
     * 
     * @return true nếu giá > 1000, ngược lại false
     */
    public boolean isExpensive() {
        return rate.compareTo(BigDecimal.valueOf(1000)) > 0;
    }

    /**
     * Áp dụng giảm giá cho sản phẩm
     * Giảm giá phải trong khoảng 0-100%
     * 
     * @param discountPercentage Phần trăm giảm giá (0-100)
     * @return Đối tượng Product sau khi giảm giá
     * 
     * Ví dụ: product.applyDiscount(BigDecimal.valueOf(15)) // Giảm 15%
     */
    public Product applyDiscount(BigDecimal discountPercentage) {
        if (discountPercentage.compareTo(BigDecimal.ZERO) > 0 && 
            discountPercentage.compareTo(BigDecimal.valueOf(100)) <= 0) {
            BigDecimal discountAmount = rate.multiply(discountPercentage).divide(BigDecimal.valueOf(100));
            this.rate = rate.subtract(discountAmount);
        }
        return this;
    }
}