-- Add document_id to file_metadata to link files to documents
ALTER TABLE file_metadata ADD COLUMN IF NOT EXISTS document_id UUID;

CREATE INDEX IF NOT EXISTS idx_file_metadata_document_id ON file_metadata(document_id);

COMMENT ON COLUMN file_metadata.document_id IS 'Reference to the document this file belongs to (nullable for standalone files)';
