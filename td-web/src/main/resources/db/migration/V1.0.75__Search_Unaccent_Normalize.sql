-- 20/10/2026 (báo lỗi người dùng: ô tìm kiếm trong danh sách gõ KHÔNG DẤU không ra kết quả, vd "cong van" không
-- khớp "Công văn"). Tìm kiếm danh sách so khớp LOWER(cột) LIKE '%từ khoá%' nên phân biệt dấu.
--
-- unaccent: extension có sẵn trong PostgreSQL contrib, "trusted" từ PG13 (chủ DB tenant tự cài được, không cần
-- superuser). td_search_norm: bỏ dấu + chữ thường, khai báo IMMUTABLE (unaccent gốc chỉ STABLE) để sau này có thể
-- đánh index biểu thức (pg_trgm) nếu cần — gọi unaccent 2 tham số với từ điển chỉ định rõ schema nên không phụ
-- thuộc search_path. "đ/Đ" là chữ riêng (không phải chữ + dấu) — translate trước cho chắc chắn.
-- Java: com.td.infrastructure.persistence.repository.SearchTextSql (chuẩn hoá từ khoá cùng quy tắc).
CREATE EXTENSION IF NOT EXISTS unaccent;

CREATE OR REPLACE FUNCTION td_search_norm(value text)
    RETURNS text
    LANGUAGE sql
    IMMUTABLE
    PARALLEL SAFE
    STRICT
AS $$
    SELECT lower(public.unaccent('public.unaccent'::regdictionary, translate(value, 'đĐ', 'dD')))
$$;
