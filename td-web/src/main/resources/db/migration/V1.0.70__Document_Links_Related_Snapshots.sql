-- Giai doan 2 bang document_links (06/10/2026 - docs/architecture/document-links.md): chuyen 2 field mang
-- "ban chup" tren van ban di / du thao sang bang lien ket:
--   documentin_obj       ("Tra loi cho van ban den")  -> vbdi_traloi_vbden / duthao_traloi_vbden
--   vanban_lienquan_obj  ("Van ban kem theo")          -> {vbdi|duthao}_kemtheo_{vbden|vbdi}
-- Moi phan tu mang (id, code, subject, unit_publish, promulgation_date, files, dbpath, database_type...)
-- luu NGUYEN VAN o cot link_data (JSONB) de API dung lai dung khuon cu cho FE.

ALTER TABLE document_links ADD COLUMN link_data JSONB NOT NULL DEFAULT '{}'::jsonb;
COMMENT ON COLUMN document_links.link_data IS 'Ban chup phan tu hien thi cua lien ket (vd {id,code,subject,unit_publish,promulgation_date,files,dbpath,database_type}) - API dung lai documentin_obj/vanban_lienquan_obj tu cot nay';

-- Phan tu mang tu field _obj: mang JSON that HOAC chuoi JSON (du lieu qua nhieu doi API). Thu tu phan tu
-- giu bang created_on tang dan theo vi tri (WITH ORDINALITY). Chi phan tu co id la UUID (van ban Java);
-- id Domino cu bi bo qua (dung chu truong khong mang du lieu Domino cu).
-- Ep chuoi -> JSONB AN TOAN: tung co chuoi rac kieu "[{id=1, code=DEN-01}]" (toString Java) luu vao field
-- _obj - cast truc tiep se lam HONG CA migration (td-web khong khoi dong duoc) -> loi thi coi la mang rong.
CREATE OR REPLACE FUNCTION pg_temp.safe_json_array(value TEXT) RETURNS JSONB AS $$
BEGIN
    IF value IS NULL OR LEFT(TRIM(value), 1) <> '[' THEN
        RETURN '[]'::jsonb;
    END IF;
    RETURN value::jsonb;
EXCEPTION WHEN others THEN
    RETURN '[]'::jsonb;
END
$$ LANGUAGE plpgsql IMMUTABLE;

CREATE OR REPLACE FUNCTION pg_temp.obj_items(fields JSONB, field_name TEXT)
RETURNS TABLE(item JSONB, pos BIGINT) AS $$
    SELECT e.value, e.ordinality
    FROM jsonb_array_elements(
        CASE jsonb_typeof(fields -> field_name)
            WHEN 'array' THEN fields -> field_name
            WHEN 'string' THEN pg_temp.safe_json_array(fields ->> field_name)
            ELSE '[]'::jsonb
        END
    ) WITH ORDINALITY AS e(value, ordinality)
$$ LANGUAGE SQL IMMUTABLE;

CREATE OR REPLACE FUNCTION pg_temp.is_uuid(value TEXT) RETURNS BOOLEAN AS $$
    SELECT value ~* '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
$$ LANGUAGE SQL IMMUTABLE;

-- Tra loi van ban den: van ban di / du thao -> van ban den
INSERT INTO document_links (source_type, source_id, target_type, target_id, link_type, link_data, created_on)
SELECT 'vbdi', d.id, 'vbden', LOWER(TRIM(i.item ->> 'id'))::uuid, 'vbdi_traloi_vbden', i.item,
       COALESCE(d.created_on, NOW()) + (i.pos * INTERVAL '1 millisecond')
FROM vbdi_documents d
CROSS JOIN LATERAL pg_temp.obj_items(d.dynamic_fields, 'documentin_obj') AS i
WHERE d.deleted_on IS NULL AND LOWER(COALESCE(d.document_type, '')) = 'vbdi'
  AND jsonb_typeof(i.item) = 'object' AND pg_temp.is_uuid(TRIM(i.item ->> 'id'))
ON CONFLICT (source_id, target_id, link_type) WHERE deleted_on IS NULL DO NOTHING;

INSERT INTO document_links (source_type, source_id, target_type, target_id, link_type, link_data, created_on)
SELECT 'duthao', d.id, 'vbden', LOWER(TRIM(i.item ->> 'id'))::uuid, 'duthao_traloi_vbden', i.item,
       COALESCE(d.created_on, NOW()) + (i.pos * INTERVAL '1 millisecond')
FROM duthao_documents d
CROSS JOIN LATERAL pg_temp.obj_items(d.dynamic_fields, 'documentin_obj') AS i
WHERE d.deleted_on IS NULL AND LOWER(COALESCE(d.document_type, '')) = 'duthao'
  AND jsonb_typeof(i.item) = 'object' AND pg_temp.is_uuid(TRIM(i.item ->> 'id'))
ON CONFLICT (source_id, target_id, link_type) WHERE deleted_on IS NULL DO NOTHING;

-- Van ban kem theo: database_type cua phan tu quyet dinh dich (vbden / vbdi); thieu = vbdi (dung quy tac
-- FE VanBanLienQuanList#handleViewDetail).
INSERT INTO document_links (source_type, source_id, target_type, target_id, link_type, link_data, created_on)
SELECT 'vbdi', d.id, t.target_type, LOWER(TRIM(i.item ->> 'id'))::uuid, 'vbdi_kemtheo_' || t.target_type, i.item,
       COALESCE(d.created_on, NOW()) + (i.pos * INTERVAL '1 millisecond')
FROM vbdi_documents d
CROSS JOIN LATERAL pg_temp.obj_items(d.dynamic_fields, 'vanban_lienquan_obj') AS i
CROSS JOIN LATERAL (SELECT CASE WHEN LOWER(COALESCE(i.item ->> 'database_type', '')) = 'vbden'
                                THEN 'vbden' ELSE 'vbdi' END AS target_type) AS t
WHERE d.deleted_on IS NULL AND LOWER(COALESCE(d.document_type, '')) = 'vbdi'
  AND jsonb_typeof(i.item) = 'object' AND pg_temp.is_uuid(TRIM(i.item ->> 'id'))
ON CONFLICT (source_id, target_id, link_type) WHERE deleted_on IS NULL DO NOTHING;

INSERT INTO document_links (source_type, source_id, target_type, target_id, link_type, link_data, created_on)
SELECT 'duthao', d.id, t.target_type, LOWER(TRIM(i.item ->> 'id'))::uuid, 'duthao_kemtheo_' || t.target_type, i.item,
       COALESCE(d.created_on, NOW()) + (i.pos * INTERVAL '1 millisecond')
FROM duthao_documents d
CROSS JOIN LATERAL pg_temp.obj_items(d.dynamic_fields, 'vanban_lienquan_obj') AS i
CROSS JOIN LATERAL (SELECT CASE WHEN LOWER(COALESCE(i.item ->> 'database_type', '')) = 'vbden'
                                THEN 'vbden' ELSE 'vbdi' END AS target_type) AS t
WHERE d.deleted_on IS NULL AND LOWER(COALESCE(d.document_type, '')) = 'duthao'
  AND jsonb_typeof(i.item) = 'object' AND pg_temp.is_uuid(TRIM(i.item ->> 'id'))
ON CONFLICT (source_id, target_id, link_type) WHERE deleted_on IS NULL DO NOTHING;
