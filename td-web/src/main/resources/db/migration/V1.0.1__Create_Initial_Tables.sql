-- Initial database schema for TD WebAPI - PostgreSQL
-- V1.0.1__Create_Initial_Tables.sql

-- Enable UUID extension
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Create brands table
CREATE TABLE brands (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name VARCHAR(100) NOT NULL UNIQUE,
    description TEXT,
    created_by UUID,
    created_on TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    last_modified_by UUID,
    last_modified_on TIMESTAMP WITH TIME ZONE,
    deleted_on TIMESTAMP WITH TIME ZONE,
    deleted_by UUID
);

-- Create products table
CREATE TABLE products (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name VARCHAR(100) NOT NULL,
    description TEXT,
    rate DECIMAL(18,2) NOT NULL CHECK (rate > 0),
    image_path VARCHAR(500),
    brand_id UUID NOT NULL,
    created_by UUID,
    created_on TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    last_modified_by UUID,
    last_modified_on TIMESTAMP WITH TIME ZONE,
    deleted_on TIMESTAMP WITH TIME ZONE,
    deleted_by UUID,
    
    CONSTRAINT FK_products_brands FOREIGN KEY (brand_id) REFERENCES brands(id)
);

-- Create indexes for better performance
CREATE INDEX IX_products_brand_id ON products(brand_id);
CREATE INDEX IX_products_name ON products(name);
CREATE INDEX IX_products_rate ON products(rate);
CREATE INDEX IX_products_created_on ON products(created_on);
CREATE INDEX IX_products_deleted_on ON products(deleted_on);

CREATE INDEX IX_brands_name ON brands(name);
CREATE INDEX IX_brands_created_on ON brands(created_on);
CREATE INDEX IX_brands_deleted_on ON brands(deleted_on);

-- Insert sample data
INSERT INTO brands (id, name, description, created_on) VALUES 
    ('11111111-1111-1111-1111-111111111111', 'Samsung', 'South Korean multinational electronics company', NOW()),
    ('22222222-2222-2222-2222-222222222222', 'Apple', 'American multinational technology company', NOW()),
    ('33333333-3333-3333-3333-333333333333', 'Sony', 'Japanese multinational conglomerate', NOW());

INSERT INTO products (id, name, description, rate, brand_id, created_on) VALUES 
    ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'Galaxy S24', 'Latest Samsung flagship smartphone', 999.99, '11111111-1111-1111-1111-111111111111', NOW()),
    ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', 'iPhone 15 Pro', 'Apple premium smartphone', 1199.99, '22222222-2222-2222-2222-222222222222', NOW()),
    ('cccccccc-cccc-cccc-cccc-cccccccccccc', 'PlayStation 5', 'Sony gaming console', 499.99, '33333333-3333-3333-3333-333333333333', NOW()),
    ('dddddddd-dddd-dddd-dddd-dddddddddddd', 'Galaxy Tab S9', 'Samsung premium tablet', 799.99, '11111111-1111-1111-1111-111111111111', NOW()),
    ('eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee', 'MacBook Pro', 'Apple laptop computer', 2399.99, '22222222-2222-2222-2222-222222222222', NOW());