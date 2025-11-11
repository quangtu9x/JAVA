package com.td.domain.search;

import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Product Document for Elasticsearch
 * 
 * Elasticsearch document đại diện cho Product entity với advanced indexing
 * cho complex search, filtering, và aggregations.
 */
@Document(indexName = "products")
@Setting(settingPath = "/elasticsearch/product-settings.json")
public class ProductDocument {

    @Id
    private String id;

    @Field(type = FieldType.Text, analyzer = "standard", searchAnalyzer = "standard")
    private String name;

    @Field(type = FieldType.Text, analyzer = "standard")
    private String description;

    @Field(type = FieldType.Keyword)
    private String slug;

    @Field(type = FieldType.Scaled_Float, scalingFactor = 100)
    private BigDecimal price;

    @Field(type = FieldType.Boolean)
    private Boolean isActive;

    @Field(type = FieldType.Date)
    private Instant createdAt;

    @Field(type = FieldType.Date)
    private Instant updatedAt;

    @Field(type = FieldType.Keyword)
    private String createdBy;

    @Field(type = FieldType.Keyword)
    private String updatedBy;

    // Brand information (denormalized for performance)
    @Field(type = FieldType.Keyword)
    private String brandId;

    @Field(type = FieldType.Text, analyzer = "standard")
    private String brandName;

    @Field(type = FieldType.Keyword)
    private String brandSlug;

    // Autocomplete fields
    @Field(type = FieldType.Search_As_You_Type)
    private String nameAutocomplete;

    @Field(type = FieldType.Search_As_You_Type)
    private String brandNameAutocomplete;

    // Categories and tags for filtering
    @Field(type = FieldType.Keyword)
    private List<String> categories;

    @Field(type = FieldType.Keyword)
    private List<String> tags;

    // Additional attributes for search
    @Field(type = FieldType.Object)
    private ProductAttributes attributes;

    // Statistics
    @Field(type = FieldType.Integer)
    private Integer viewCount = 0;

    @Field(type = FieldType.Integer)
    private Integer orderCount = 0;

    @Field(type = FieldType.Float)
    private Float rating = 0.0f;

    // Constructor mặc định
    public ProductDocument() {}

    // Constructor with essential fields
    public ProductDocument(String id, String name, String description, String slug, 
                          BigDecimal price, Boolean isActive, String brandId, String brandName) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.slug = slug;
        this.price = price;
        this.isActive = isActive;
        this.brandId = brandId;
        this.brandName = brandName;
        this.nameAutocomplete = name;
        this.brandNameAutocomplete = brandName;
    }

    // Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
        this.nameAutocomplete = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getSlug() {
        return slug;
    }

    public void setSlug(String slug) {
        this.slug = slug;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }

    public String getBrandId() {
        return brandId;
    }

    public void setBrandId(String brandId) {
        this.brandId = brandId;
    }

    public String getBrandName() {
        return brandName;
    }

    public void setBrandName(String brandName) {
        this.brandName = brandName;
        this.brandNameAutocomplete = brandName;
    }

    public String getBrandSlug() {
        return brandSlug;
    }

    public void setBrandSlug(String brandSlug) {
        this.brandSlug = brandSlug;
    }

    public String getNameAutocomplete() {
        return nameAutocomplete;
    }

    public void setNameAutocomplete(String nameAutocomplete) {
        this.nameAutocomplete = nameAutocomplete;
    }

    public String getBrandNameAutocomplete() {
        return brandNameAutocomplete;
    }

    public void setBrandNameAutocomplete(String brandNameAutocomplete) {
        this.brandNameAutocomplete = brandNameAutocomplete;
    }

    public List<String> getCategories() {
        return categories;
    }

    public void setCategories(List<String> categories) {
        this.categories = categories;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    public ProductAttributes getAttributes() {
        return attributes;
    }

    public void setAttributes(ProductAttributes attributes) {
        this.attributes = attributes;
    }

    public Integer getViewCount() {
        return viewCount;
    }

    public void setViewCount(Integer viewCount) {
        this.viewCount = viewCount;
    }

    public Integer getOrderCount() {
        return orderCount;
    }

    public void setOrderCount(Integer orderCount) {
        this.orderCount = orderCount;
    }

    public Float getRating() {
        return rating;
    }

    public void setRating(Float rating) {
        this.rating = rating;
    }

    /**
     * Nested class for product attributes
     */
    public static class ProductAttributes {
        
        @Field(type = FieldType.Keyword)
        private String color;

        @Field(type = FieldType.Keyword)
        private String size;

        @Field(type = FieldType.Keyword)
        private String material;

        @Field(type = FieldType.Float)
        private Float weight;

        @Field(type = FieldType.Object)
        private Dimensions dimensions;

        // Constructors
        public ProductAttributes() {}

        // Getters and Setters
        public String getColor() {
            return color;
        }

        public void setColor(String color) {
            this.color = color;
        }

        public String getSize() {
            return size;
        }

        public void setSize(String size) {
            this.size = size;
        }

        public String getMaterial() {
            return material;
        }

        public void setMaterial(String material) {
            this.material = material;
        }

        public Float getWeight() {
            return weight;
        }

        public void setWeight(Float weight) {
            this.weight = weight;
        }

        public Dimensions getDimensions() {
            return dimensions;
        }

        public void setDimensions(Dimensions dimensions) {
            this.dimensions = dimensions;
        }

        public static class Dimensions {
            
            @Field(type = FieldType.Float)
            private Float length;

            @Field(type = FieldType.Float)
            private Float width;

            @Field(type = FieldType.Float)
            private Float height;

            public Dimensions() {}

            public Dimensions(Float length, Float width, Float height) {
                this.length = length;
                this.width = width;
                this.height = height;
            }

            // Getters and Setters
            public Float getLength() {
                return length;
            }

            public void setLength(Float length) {
                this.length = length;
            }

            public Float getWidth() {
                return width;
            }

            public void setWidth(Float width) {
                this.width = width;
            }

            public Float getHeight() {
                return height;
            }

            public void setHeight(Float height) {
                this.height = height;
            }
        }
    }

    @Override
    public String toString() {
        return "ProductDocument{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", slug='" + slug + '\'' +
                ", price=" + price +
                ", brandName='" + brandName + '\'' +
                ", isActive=" + isActive +
                '}';
    }
}