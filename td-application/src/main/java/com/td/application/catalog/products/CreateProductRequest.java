package com.td.application.catalog.products;

import lombok.Data;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.UUID;

@Data
public class CreateProductRequest {
    
    @NotBlank(message = "Tên sản phẩm không được để trống")
    @Size(min = 2, max = 100, message = "Tên sản phẩm phải từ 2 đến 100 ký tự")
    private String name;
    
    @Size(max = 1000, message = "Mô tả không vượt quá 1000 ký tự")
    private String description;
    
    @NotNull(message = "Giá không được để trống")
    @DecimalMin(value = "0.0", inclusive = false, message = "Giá phải lớn hơn 0")
    @Digits(integer = 16, fraction = 2, message = "Giá có tối đa 16 chữ số nguyên và 2 chữ số thập phân")
    private BigDecimal rate;
    
    @NotNull(message = "ID thương hiệu không được để trống")
    private UUID brandId;
    
    private String imagePath;
}