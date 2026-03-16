package com.td.domain.catalog;

import com.td.domain.common.contracts.AuditableEntity;
import com.td.domain.common.contracts.IAggregateRoot;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Entity Thương hiệu - Quản lý thông tin thương hiệu sản phẩm
 * 
 * Domain Aggregate Root cho việc quản lý thương hiệu
 * Kế thừa từ AuditableEntity để tự động tracking thông tin audit
 * 
 * Quan hệ: Một thương hiệu có nhiều sản phẩm (One-to-Many)
 * 
 * Sử dụng:
 * - Tạo mới: new Brand(name, description)
 * - Cập nhật: brand.update(newName, newDescription)
 * - Thêm sản phẩm: brand.addProduct(product)
 * - Xóa sản phẩm: brand.removeProduct(product)
 * - Đếm sản phẩm: brand.getProductCount()
 */
@Entity
@Table(name = "brands")
@Getter
@Setter(AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Brand extends AuditableEntity<UUID> implements IAggregateRoot {

    // Tên thương hiệu (bắt buộc, duy nhất)
    @Column(nullable = false, unique = true)
    private String name;

    // Mô tả thương hiệu
    @Column(columnDefinition = "TEXT")
    private String description;

    // Navigation property - Danh sách sản phẩm thuộc thương hiệu (lazy loading)
    @OneToMany(mappedBy = "brand", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Product> products = new ArrayList<>();

    /**
     * Constructor - Tạo thương hiệu mới
     * 
     * @param name Tên thương hiệu (bắt buộc, phải unique)
     * @param description Mô tả thương hiệu
     */
    public Brand(String name, String description) {
        this.name = name;
        this.description = description;
    }

    /**
     * Cập nhật thông tin thương hiệu
     * Chỉ cập nhật các trường có giá trị mới khác null và khác giá trị hiện tại
     * 
     * @param name Tên mới (null = không thay đổi)
     * @param description Mô tả mới (null = không thay đổi)
     * @return Đối tượng Brand sau khi cập nhật (cho phép method chaining)
     */
    public Brand update(String name, String description) {
        if (name != null && !this.name.equals(name)) {
            this.name = name;
        }
        if (description != null && !description.equals(this.description)) {
            this.description = description;
        }
        return this;
    }

    // === Business Methods - Các phương thức nghiệp vụ ===

    /**
     * Đếm số lượng sản phẩm thuộc thương hiệu
     * 
     * @return Số lượng sản phẩm
     */
    public int getProductCount() {
        return products.size();
    }

    /**
     * Kiểm tra thương hiệu có sản phẩm nào không
     * 
     * @return true nếu có ít nhất 1 sản phẩm, ngược lại false
     */
    public boolean hasProducts() {
        return !products.isEmpty();
    }

    /**
     * Thêm sản phẩm vào thương hiệu
     * 
     * @param product Sản phẩm cần thêm (không null)
     */
    public void addProduct(Product product) {
        if (product != null) {
            products.add(product);
        }
    }

    /**
     * Xóa sản phẩm khỏi thương hiệu
     * 
     * @param product Sản phẩm cần xóa (không null)
     */
    public void removeProduct(Product product) {
        if (product != null) {
            products.remove(product);
        }
    }
}