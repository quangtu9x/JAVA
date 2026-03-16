-- Create file_metadata table for storing file information
CREATE TABLE file_metadata (
    id UUID PRIMARY KEY,
    original_filename VARCHAR(255) NOT NULL,
    stored_filename VARCHAR(255) NOT NULL UNIQUE,
    file_path VARCHAR(500) NOT NULL,
    file_size BIGINT NOT NULL,
    content_type VARCHAR(255),
    file_extension VARCHAR(20),
    file_category VARCHAR(50) NOT NULL,
    bucket_name VARCHAR(100) NOT NULL,
    uploaded_by UUID,
    uploaded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    download_count BIGINT DEFAULT 0,
    last_downloaded_at TIMESTAMP,
    is_public BOOLEAN DEFAULT FALSE,
    description TEXT,
    tags TEXT,
    metadata TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Create indexes for better performance
CREATE INDEX idx_file_metadata_category ON file_metadata(file_category);
CREATE INDEX idx_file_metadata_uploaded_by ON file_metadata(uploaded_by);
CREATE INDEX idx_file_metadata_uploaded_at ON file_metadata(uploaded_at);
CREATE INDEX idx_file_metadata_content_type ON file_metadata(content_type);
CREATE INDEX idx_file_metadata_is_public ON file_metadata(is_public);
CREATE INDEX idx_file_metadata_stored_filename ON file_metadata(stored_filename);

-- Create trigger to update updated_at timestamp
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

CREATE TRIGGER update_file_metadata_updated_at 
    BEFORE UPDATE ON file_metadata 
    FOR EACH ROW 
    EXECUTE FUNCTION update_updated_at_column();

-- Add comments for documentation
COMMENT ON TABLE file_metadata IS 'Stores metadata information for files stored in MinIO';
COMMENT ON COLUMN file_metadata.original_filename IS 'Original filename as uploaded by user';
COMMENT ON COLUMN file_metadata.stored_filename IS 'Unique filename used for storage in MinIO';
COMMENT ON COLUMN file_metadata.file_path IS 'Full path to file in MinIO bucket';
COMMENT ON COLUMN file_metadata.file_category IS 'Category: PRODUCT, BRAND, USER, DOCUMENT, TEMPORARY, SYSTEM, MARKETING, SUPPORT';
COMMENT ON COLUMN file_metadata.bucket_name IS 'MinIO bucket name where file is stored';
COMMENT ON COLUMN file_metadata.download_count IS 'Number of times file has been downloaded';
COMMENT ON COLUMN file_metadata.is_public IS 'Whether file is publicly accessible';
COMMENT ON COLUMN file_metadata.tags IS 'JSON array of tags for file categorization';
COMMENT ON COLUMN file_metadata.metadata IS 'Additional JSON metadata for file';