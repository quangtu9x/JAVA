-- Create documents table for dynamic document management
CREATE TABLE documents (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    title VARCHAR(300) NOT NULL,
    document_type VARCHAR(100),
    status VARCHAR(50),
    content TEXT,
    tags_json TEXT NOT NULL DEFAULT '[]',
    attributes_json TEXT NOT NULL DEFAULT '{}',
    metadata_json TEXT NOT NULL DEFAULT '{}',
    version_no BIGINT NOT NULL DEFAULT 1,
    created_by UUID,
    created_on TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    last_modified_by UUID,
    last_modified_on TIMESTAMP WITH TIME ZONE,
    deleted_on TIMESTAMP WITH TIME ZONE,
    deleted_by UUID
);

CREATE INDEX IX_documents_title ON documents(title);
CREATE INDEX IX_documents_document_type ON documents(document_type);
CREATE INDEX IX_documents_status ON documents(status);
CREATE INDEX IX_documents_created_on ON documents(created_on);
CREATE INDEX IX_documents_last_modified_on ON documents(last_modified_on);
CREATE INDEX IX_documents_deleted_on ON documents(deleted_on);

COMMENT ON TABLE documents IS 'Business documents with flexible unstructured fields';
COMMENT ON COLUMN documents.tags_json IS 'JSON array for document tags';
COMMENT ON COLUMN documents.attributes_json IS 'JSON object for dynamic business attributes';
COMMENT ON COLUMN documents.metadata_json IS 'JSON object for system metadata';