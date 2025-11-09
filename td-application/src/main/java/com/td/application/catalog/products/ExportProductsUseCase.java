package com.td.application.catalog.products;

import com.td.infrastructure.persistence.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.td.domain.catalog.Product;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ExportProductsUseCase {
    
    private final ProductRepository productRepository;

    public byte[] execute(ExportProductsRequest request) {
        try {
            // Build specification for filtering
            Specification<Product> spec = Specification.where(null);
            
            if (request.getName() != null && !request.getName().trim().isEmpty()) {
                spec = spec.and(ProductRepository.withName(request.getName()));
            }
            
            if (request.getDescription() != null && !request.getDescription().trim().isEmpty()) {
                spec = spec.and(ProductRepository.withDescription(request.getDescription()));
            }
            
            if (request.getBrandName() != null && !request.getBrandName().trim().isEmpty()) {
                spec = spec.and(ProductRepository.withBrandName(request.getBrandName()));
            }

            // Get all matching products
            List<Product> products = productRepository.findAll(spec);
            
            // Generate CSV content
            return generateCsv(products);
            
        } catch (Exception ex) {
            // Return empty CSV on error
            return "Error generating export".getBytes(StandardCharsets.UTF_8);
        }
    }

    private byte[] generateCsv(List<Product> products) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        
        // CSV Header
        String header = "ID,Name,Description,Rate,Brand ID,Brand Name,Created On,Last Modified On\n";
        outputStream.write(header.getBytes(StandardCharsets.UTF_8));
        
        // CSV Data
        for (Product product : products) {
            String line = String.format("%s,\"%s\",\"%s\",%s,%s,\"%s\",%s,%s\n",
                product.getId(),
                escapeCsv(product.getName()),
                escapeCsv(product.getDescription()),
                product.getRate(),
                product.getBrandId(),
                product.getBrand() != null ? escapeCsv(product.getBrand().getName()) : "",
                product.getCreatedOn(),
                product.getLastModifiedOn()
            );
            outputStream.write(line.getBytes(StandardCharsets.UTF_8));
        }
        
        return outputStream.toByteArray();
    }
    
    private String escapeCsv(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\"", "\"\"");
    }
}