-- Yeu cau nguoi dung 06/10/2026: "thay doi cach quan ly lien ket, thay vi phai ghi truc tiep vao tung
-- van ban" - bang lien ket rieng document_links (1 dong = 1 lien ket co huong), thay cho cac field id noi
-- ";" ghi cheo trong dynamic_fields cua CA 2 phia (document_profiles_id, *_attached_id,
-- document_profiles_from_draft_id...). Xem docs/architecture/document-links.md.
--
-- Giai doan 1: lien ket HO SO CONG VIEC <-> van ban + DU THAO -> VAN BAN DI (phat hanh tu du thao).
-- Field cu VAN DUOC GHI SONG SONG (dual-write) cho toi khi moi noi doc da chuyen sang bang nay.

CREATE TABLE document_links (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    source_type VARCHAR(50) NOT NULL,
    source_id UUID NOT NULL,
    target_type VARCHAR(50) NOT NULL,
    target_id UUID NOT NULL,
    link_type VARCHAR(50) NOT NULL,
    version_no BIGINT NOT NULL DEFAULT 1,
    created_by UUID,
    created_on TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    last_modified_by UUID,
    last_modified_on TIMESTAMP WITH TIME ZONE,
    deleted_on TIMESTAMP WITH TIME ZONE,
    deleted_by UUID
);

-- 1 lien ket dang hieu luc / (nguon, dich, loai). Go = xoa mem; gan lai sau khi go tao dong moi.
CREATE UNIQUE INDEX UX_document_links_active
    ON document_links(source_id, target_id, link_type) WHERE deleted_on IS NULL;
CREATE INDEX IX_document_links_source ON document_links(source_id, link_type) WHERE deleted_on IS NULL;
CREATE INDEX IX_document_links_target ON document_links(target_id, link_type) WHERE deleted_on IS NULL;

COMMENT ON TABLE document_links IS 'Lien ket co huong giua 2 van ban (thay field id noi ";" trong dynamic_fields). Go lien ket = xoa mem.';
COMMENT ON COLUMN document_links.link_type IS 'hoso_duthao | hoso_vbdi | hoso_vbdi_phathanh | hoso_vbden | hoso_vbden_xuly | hoso_phieutrinh | duthao_vbdi (xem DocumentLinkType)';
COMMENT ON COLUMN document_links.source_type IS 'Loai van ban nguon: hosocv | duthao';
COMMENT ON COLUMN document_links.target_type IS 'Loai van ban dich: duthao | vbdi | vbden | phieutrinh';

-- ---------------------------------------------------------------------------------------------------
-- BACKFILL tu field cu. Field co the la chuoi "a;b" HOAC mang JSON (du lieu qua nhieu doi API) -> gom ve
-- chuoi roi tach ";". Chi giu id khop DUNG 1 ho so / van ban Java (UUID) dang ton tai - id Domino cu
-- (UNID 32 hex) khong khop duoc nen tu bi bo qua (dung chu truong "khong mang du lieu Domino cu").
-- ---------------------------------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION pg_temp.link_field_text(fields JSONB, field_name TEXT) RETURNS TEXT AS $$
    SELECT CASE jsonb_typeof(fields -> field_name)
        WHEN 'array' THEN (SELECT string_agg(e, ';') FROM jsonb_array_elements_text(fields -> field_name) e)
        WHEN 'string' THEN fields ->> field_name
        ELSE NULL
    END
$$ LANGUAGE SQL IMMUTABLE;

-- Ho so -> du thao (gankemduthao / du thao tao tu ho so)
INSERT INTO document_links (source_type, source_id, target_type, target_id, link_type, created_on)
SELECT DISTINCT 'hosocv', h.id, 'duthao', d.id, 'hoso_duthao', COALESCE(d.last_modified_on, d.created_on, NOW())
FROM duthao_documents d
CROSS JOIN LATERAL regexp_split_to_table(pg_temp.link_field_text(d.dynamic_fields, 'document_profiles_id'), ';') AS v(raw)
JOIN hosocv_documents h ON h.id::text = LOWER(TRIM(v.raw))
WHERE d.deleted_on IS NULL AND LOWER(COALESCE(d.document_type, '')) = 'duthao'
ON CONFLICT (source_id, target_id, link_type) WHERE deleted_on IS NULL DO NOTHING;

-- Ho so -> van ban di lien quan (ganvanbandi)
INSERT INTO document_links (source_type, source_id, target_type, target_id, link_type, created_on)
SELECT DISTINCT 'hosocv', h.id, 'vbdi', d.id, 'hoso_vbdi', COALESCE(d.last_modified_on, d.created_on, NOW())
FROM vbdi_documents d
CROSS JOIN LATERAL regexp_split_to_table(pg_temp.link_field_text(d.dynamic_fields, 'document_profiles_id'), ';') AS v(raw)
JOIN hosocv_documents h ON h.id::text = LOWER(TRIM(v.raw))
WHERE d.deleted_on IS NULL AND LOWER(COALESCE(d.document_type, '')) = 'vbdi'
ON CONFLICT (source_id, target_id, link_type) WHERE deleted_on IS NULL DO NOTHING;

-- Ho so -> van ban di phat hanh (ganvanbanphathanh)
INSERT INTO document_links (source_type, source_id, target_type, target_id, link_type, created_on)
SELECT DISTINCT 'hosocv', h.id, 'vbdi', d.id, 'hoso_vbdi_phathanh', COALESCE(d.last_modified_on, d.created_on, NOW())
FROM vbdi_documents d
CROSS JOIN LATERAL regexp_split_to_table(pg_temp.link_field_text(d.dynamic_fields, 'issuance_document_profiles_id'), ';') AS v(raw)
JOIN hosocv_documents h ON h.id::text = LOWER(TRIM(v.raw))
WHERE d.deleted_on IS NULL AND LOWER(COALESCE(d.document_type, '')) = 'vbdi'
ON CONFLICT (source_id, target_id, link_type) WHERE deleted_on IS NULL DO NOTHING;

-- Ho so -> van ban di phat hanh TU DU THAO cua ho so (document_profiles_from_draft_id - ChuyenThanhVanBanDi /
-- gan du thao da co van ban di). Ghi tuong minh cung loai hoso_vbdi_phathanh de "go" o tab Van ban phat
-- hanh go duoc (xem docs/architecture/document-links.md).
INSERT INTO document_links (source_type, source_id, target_type, target_id, link_type, created_on)
SELECT DISTINCT 'hosocv', h.id, 'vbdi', d.id, 'hoso_vbdi_phathanh', COALESCE(d.last_modified_on, d.created_on, NOW())
FROM vbdi_documents d
CROSS JOIN LATERAL regexp_split_to_table(pg_temp.link_field_text(d.dynamic_fields, 'document_profiles_from_draft_id'), ';') AS v(raw)
JOIN hosocv_documents h ON h.id::text = LOWER(TRIM(v.raw))
WHERE d.deleted_on IS NULL AND LOWER(COALESCE(d.document_type, '')) = 'vbdi'
ON CONFLICT (source_id, target_id, link_type) WHERE deleted_on IS NULL DO NOTHING;

-- Ho so -> van ban den lien quan (ganvanbanden)
INSERT INTO document_links (source_type, source_id, target_type, target_id, link_type, created_on)
SELECT DISTINCT 'hosocv', h.id, 'vbden', d.id, 'hoso_vbden', COALESCE(d.last_modified_on, d.created_on, NOW())
FROM documents d
CROSS JOIN LATERAL regexp_split_to_table(pg_temp.link_field_text(d.dynamic_fields, 'document_profiles_id'), ';') AS v(raw)
JOIN hosocv_documents h ON h.id::text = LOWER(TRIM(v.raw))
WHERE d.deleted_on IS NULL AND LOWER(COALESCE(d.document_type, '')) = 'vbden'
ON CONFLICT (source_id, target_id, link_type) WHERE deleted_on IS NULL DO NOTHING;

-- Ho so -> van ban den can xu ly (ganvanbandenxuly)
INSERT INTO document_links (source_type, source_id, target_type, target_id, link_type, created_on)
SELECT DISTINCT 'hosocv', h.id, 'vbden', d.id, 'hoso_vbden_xuly', COALESCE(d.last_modified_on, d.created_on, NOW())
FROM documents d
CROSS JOIN LATERAL regexp_split_to_table(pg_temp.link_field_text(d.dynamic_fields, 'document_profiles_handling_id'), ';') AS v(raw)
JOIN hosocv_documents h ON h.id::text = LOWER(TRIM(v.raw))
WHERE d.deleted_on IS NULL AND LOWER(COALESCE(d.document_type, '')) = 'vbden'
ON CONFLICT (source_id, target_id, link_type) WHERE deleted_on IS NULL DO NOTHING;

-- Du thao -> van ban di phat hanh tu du thao: van ban di.draft_id (ChuyenThanhVanBanDi) HOAC
-- du thao.document_out_id - hop ca 2 phia.
INSERT INTO document_links (source_type, source_id, target_type, target_id, link_type, created_on)
SELECT DISTINCT 'duthao', dt.id, 'vbdi', v.id, 'duthao_vbdi', COALESCE(v.created_on, NOW())
FROM vbdi_documents v
JOIN duthao_documents dt ON dt.id::text = LOWER(TRIM(v.dynamic_fields ->> 'draft_id'))
WHERE v.deleted_on IS NULL AND LOWER(COALESCE(v.document_type, '')) = 'vbdi'
  AND LOWER(COALESCE(dt.document_type, '')) = 'duthao'
ON CONFLICT (source_id, target_id, link_type) WHERE deleted_on IS NULL DO NOTHING;

INSERT INTO document_links (source_type, source_id, target_type, target_id, link_type, created_on)
SELECT DISTINCT 'duthao', dt.id, 'vbdi', v.id, 'duthao_vbdi', COALESCE(v.created_on, NOW())
FROM duthao_documents dt
JOIN vbdi_documents v ON v.id::text = LOWER(TRIM(dt.dynamic_fields ->> 'document_out_id'))
WHERE v.deleted_on IS NULL AND LOWER(COALESCE(v.document_type, '')) = 'vbdi'
  AND LOWER(COALESCE(dt.document_type, '')) = 'duthao'
ON CONFLICT (source_id, target_id, link_type) WHERE deleted_on IS NULL DO NOTHING;
