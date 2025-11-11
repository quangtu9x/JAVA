package com.td.infrastructure.search.service;

import com.td.domain.catalog.Brand;
import com.td.domain.catalog.Product;
import com.td.domain.search.BrandDocument;
import com.td.domain.search.ProductDocument;
import com.td.infrastructure.search.repository.BrandElasticsearchRepository;
import com.td.infrastructure.search.repository.ProductElasticsearchRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import java.util.List;
import java.util.Optional;

/**
 * Data Synchronization Service
 * 
 * Service đồng bộ dữ liệu từ PostgreSQL sang Elasticsearch.
 * Sử dụng khi có thay đổi dữ liệu trong database để update search index.
 */
@Service
public class DataSynchronizationService {

    private static final Logger logger = LoggerFactory.getLogger(DataSynchronizationService.class);

    private final EntityManager entityManager;
    private final ProductElasticsearchRepository productSearchRepository;
    private final BrandElasticsearchRepository brandSearchRepository;

    public DataSynchronizationService(EntityManager entityManager,
                                    ProductElasticsearchRepository productSearchRepository,
                                    BrandElasticsearchRepository brandSearchRepository) {
        this.entityManager = entityManager;
        this.productSearchRepository = productSearchRepository;
        this.brandSearchRepository = brandSearchRepository;
    }

    /**
     * Sync tất cả brands từ database sang Elasticsearch
     */
    @Transactional(readOnly = true)
    public void syncAllBrands() {
        logger.info("Starting full brand synchronization");
        
        try {
            TypedQuery<Brand> query = entityManager.createQuery("SELECT b FROM Brand b", Brand.class);
            List<Brand> brands = query.getResultList();
            
            logger.info("Found {} brands to sync", brands.size());
            
            for (Brand brand : brands) {
                syncBrand(brand);
            }
            
            logger.info("Brand synchronization completed successfully");
            
        } catch (Exception e) {
            logger.error("Error during brand synchronization", e);
            throw new RuntimeException("Failed to sync brands", e);
        }
    }

    /**
     * Sync tất cả products từ database sang Elasticsearch
     */
    @Transactional(readOnly = true)
    public void syncAllProducts() {
        logger.info("Starting full product synchronization");
        
        try {
            TypedQuery<Product> query = entityManager.createQuery(
                "SELECT p FROM Product p LEFT JOIN FETCH p.brand", Product.class);
            List<Product> products = query.getResultList();
            
            logger.info("Found {} products to sync", products.size());
            
            for (Product product : products) {
                syncProduct(product);
            }
            
            logger.info("Product synchronization completed successfully");
            
        } catch (Exception e) {
            logger.error("Error during product synchronization", e);
            throw new RuntimeException("Failed to sync products", e);
        }
    }

    /**
     * Sync một brand cụ thể
     */
    public void syncBrand(Brand brand) {
        try {
            logger.debug("Syncing brand: {}", brand.getName());
            
            BrandDocument brandDoc = convertBrandToDocument(brand);
            
            // Tính số lượng products của brand
            Long productCount = getProductCountForBrand(brand.getId());
            brandDoc.setProductCount(productCount.intValue());
            
            brandSearchRepository.save(brandDoc);
            
            logger.debug("Brand {} synced successfully", brand.getName());
            
        } catch (Exception e) {
            logger.error("Error syncing brand: {}", brand.getName(), e);
            throw new RuntimeException("Failed to sync brand: " + brand.getName(), e);
        }
    }

    /**
     * Sync một product cụ thể
     */
    public void syncProduct(Product product) {
        try {
            logger.debug("Syncing product: {}", product.getName());
            
            ProductDocument productDoc = convertProductToDocument(product);
            productSearchRepository.save(productDoc);
            
            logger.debug("Product {} synced successfully", product.getName());
            
        } catch (Exception e) {
            logger.error("Error syncing product: {}", product.getName(), e);
            throw new RuntimeException("Failed to sync product: " + product.getName(), e);
        }
    }

    /**
     * Xóa brand từ Elasticsearch
     */
    public void deleteBrandFromIndex(String brandId) {
        try {
            logger.info("Deleting brand from search index: {}", brandId);
            brandSearchRepository.deleteById(brandId);
            
            // Cũng cần xóa tất cả products của brand này
            deleteProductsByBrandFromIndex(brandId);
            
        } catch (Exception e) {
            logger.error("Error deleting brand from index: {}", brandId, e);
        }
    }

    /**
     * Xóa product từ Elasticsearch
     */
    public void deleteProductFromIndex(String productId) {
        try {
            logger.info("Deleting product from search index: {}", productId);
            productSearchRepository.deleteById(productId);
            
        } catch (Exception e) {
            logger.error("Error deleting product from index: {}", productId, e);
        }
    }

    /**
     * Xóa tất cả products của một brand từ index
     */
    private void deleteProductsByBrandFromIndex(String brandId) {
        try {
            List<ProductDocument> brandProducts = productSearchRepository.findByBrandIdAndIsActive(brandId, true, 
                org.springframework.data.domain.PageRequest.of(0, 1000)).getContent();
            
            for (ProductDocument product : brandProducts) {
                productSearchRepository.deleteById(product.getId());
            }
            
            logger.info("Deleted {} products for brand {}", brandProducts.size(), brandId);
            
        } catch (Exception e) {
            logger.error("Error deleting products for brand: {}", brandId, e);
        }
    }

    /**
     * Rebuild toàn bộ search index
     */
    @Transactional(readOnly = true)
    public void rebuildSearchIndex() {
        logger.info("Starting full search index rebuild");
        
        try {
            // Xóa tất cả documents hiện tại
            logger.info("Clearing existing search index");
            productSearchRepository.deleteAll();
            brandSearchRepository.deleteAll();
            
            // Sync lại tất cả dữ liệu
            syncAllBrands();
            syncAllProducts();
            
            logger.info("Search index rebuild completed successfully");
            
        } catch (Exception e) {
            logger.error("Error during search index rebuild", e);
            throw new RuntimeException("Failed to rebuild search index", e);
        }
    }

    /**
     * Convert Brand entity thành BrandDocument
     */
    private BrandDocument convertBrandToDocument(Brand brand) {
        BrandDocument doc = new BrandDocument();
        doc.setId(brand.getId().toString());
        doc.setName(brand.getName());
        doc.setDescription(brand.getDescription());
        doc.setSlug(brand.getSlug());
        doc.setIsActive(brand.getIsActive());
        doc.setCreatedAt(brand.getCreatedAt());
        doc.setUpdatedAt(brand.getUpdatedAt());
        doc.setCreatedBy(brand.getCreatedBy());
        doc.setUpdatedBy(brand.getUpdatedBy());
        
        return doc;
    }

    /**
     * Convert Product entity thành ProductDocument
     */
    private ProductDocument convertProductToDocument(Product product) {
        ProductDocument doc = new ProductDocument();
        doc.setId(product.getId().toString());
        doc.setName(product.getName());
        doc.setDescription(product.getDescription());
        doc.setSlug(product.getSlug());
        doc.setPrice(product.getPrice());
        doc.setIsActive(product.getIsActive());
        doc.setCreatedAt(product.getCreatedAt());
        doc.setUpdatedAt(product.getUpdatedAt());
        doc.setCreatedBy(product.getCreatedBy());
        doc.setUpdatedBy(product.getUpdatedBy());
        
        // Brand information (denormalized)
        if (product.getBrand() != null) {
            doc.setBrandId(product.getBrand().getId().toString());
            doc.setBrandName(product.getBrand().getName());
            doc.setBrandSlug(product.getBrand().getSlug());
        }
        
        return doc;
    }

    /**
     * Lấy số lượng products của một brand
     */
    private Long getProductCountForBrand(Long brandId) {
        try {
            TypedQuery<Long> query = entityManager.createQuery(
                "SELECT COUNT(p) FROM Product p WHERE p.brand.id = :brandId AND p.isActive = true", Long.class);
            query.setParameter("brandId", brandId);
            return query.getSingleResult();
        } catch (Exception e) {
            logger.warn("Error counting products for brand {}: {}", brandId, e.getMessage());
            return 0L;
        }
    }

    /**
     * Check nếu brand exists trong database
     */
    public boolean brandExistsInDatabase(String brandId) {
        try {
            TypedQuery<Long> query = entityManager.createQuery(
                "SELECT COUNT(b) FROM Brand b WHERE b.id = :brandId", Long.class);
            query.setParameter("brandId", Long.parseLong(brandId));
            return query.getSingleResult() > 0;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Check nếu product exists trong database
     */
    public boolean productExistsInDatabase(String productId) {
        try {
            TypedQuery<Long> query = entityManager.createQuery(
                "SELECT COUNT(p) FROM Product p WHERE p.id = :productId", Long.class);
            query.setParameter("productId", Long.parseLong(productId));
            return query.getSingleResult() > 0;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Sync theo batch để tránh memory issues
     */
    @Transactional(readOnly = true)
    public void syncAllProductsBatch(int batchSize) {
        logger.info("Starting batch product synchronization with batch size: {}", batchSize);
        
        try {
            int offset = 0;
            int totalSynced = 0;
            
            while (true) {
                TypedQuery<Product> query = entityManager.createQuery(
                    "SELECT p FROM Product p LEFT JOIN FETCH p.brand ORDER BY p.id", Product.class);
                query.setFirstResult(offset);
                query.setMaxResults(batchSize);
                
                List<Product> products = query.getResultList();
                
                if (products.isEmpty()) {
                    break;
                }
                
                for (Product product : products) {
                    syncProduct(product);
                    totalSynced++;
                }
                
                logger.info("Synced batch: {} products (Total: {})", products.size(), totalSynced);
                offset += batchSize;
                
                // Clear persistence context để tránh memory leak
                entityManager.clear();
            }
            
            logger.info("Batch product synchronization completed. Total synced: {}", totalSynced);
            
        } catch (Exception e) {
            logger.error("Error during batch product synchronization", e);
            throw new RuntimeException("Failed to sync products in batch", e);
        }
    }
}