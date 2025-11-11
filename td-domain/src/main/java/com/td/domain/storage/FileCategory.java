package com.td.domain.storage;

/**
 * Enum representing different categories of files in the system
 */
public enum FileCategory {
    /**
     * Product-related files (images, documents, specifications)
     */
    PRODUCT("Product Files", "products"),
    
    /**
     * Brand-related files (logos, banners, marketing materials)
     */
    BRAND("Brand Files", "brands"),
    
    /**
     * User-related files (avatars, profile pictures)
     */
    USER("User Files", "users"),
    
    /**
     * General documents (contracts, invoices, reports)
     */
    DOCUMENT("Documents", "documents"),
    
    /**
     * Temporary files (uploads in progress, cache files)
     */
    TEMPORARY("Temporary Files", "temp"),
    
    /**
     * System files (backups, exports, logs)
     */
    SYSTEM("System Files", "system"),
    
    /**
     * Marketing materials (banners, promotions, campaigns)
     */
    MARKETING("Marketing Files", "marketing"),
    
    /**
     * Support files (manuals, guides, help documents)
     */
    SUPPORT("Support Files", "support");

    private final String displayName;
    private final String directoryPath;

    FileCategory(String displayName, String directoryPath) {
        this.displayName = displayName;
        this.directoryPath = directoryPath;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDirectoryPath() {
        return directoryPath;
    }

    /**
     * Get the full directory path for MinIO storage
     */
    public String getFullPath() {
        return directoryPath + "/";
    }

    /**
     * Find FileCategory by directory path
     */
    public static FileCategory fromDirectoryPath(String directoryPath) {
        for (FileCategory category : values()) {
            if (category.directoryPath.equals(directoryPath)) {
                return category;
            }
        }
        throw new IllegalArgumentException("Unknown directory path: " + directoryPath);
    }
}