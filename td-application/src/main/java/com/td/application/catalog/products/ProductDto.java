package com.td.application.catalog.products;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO - Data Transfer Object cho Product
 * 
 * Đối tượng truyền dữ liệu giữa các layer (Application <-> Web)
 * Chứa các thông tin cần thiết để hiển thị sản phẩm, bao gồm cả brandName (join từ bảng brands)
 * 
 * Sử dụng trong:
 * - Response của API GET /products
 * - Search/Filter products
 * - Get product details
 */
@Data
public class ProductDto {
    
    // ID sản phẩm (UUID)
    private UUID id;
    
    // Tên sản phẩm
    private String name;
    
    // Mô tả sản phẩm
    private String description;
    
    // Giá sản phẩm
    private BigDecimal rate;
    
    // Đường dẫn hình ảnh
    private String imagePath;
    
    // ID thương hiệu (foreign key)
    private UUID brandId;
    
    // Tên thương hiệu (join từ bảng brands, để hiển thị)
    private String brandName;
    
    // Thời gian tạo
    private LocalDateTime createdOn;
    
    // Thời gian sửa cuối
    private LocalDateTime lastModifiedOn;
}