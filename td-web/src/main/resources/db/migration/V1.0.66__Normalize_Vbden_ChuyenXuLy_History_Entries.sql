-- 27/09/2026 (bao loi nguoi dung "van ban den -> chu tri xu ly -> cot Nguoi chuyen thinh thoang khong
-- the hien thong tin (demo3)"): truoc ban sua nay ChuyenXuLyVbdenUseCase ghi moi phan tu
-- dynamic_fields.chuyenxuly_obj theo khoa TU DAT {nguoi_chuyen, thoi_gian (ISO), user_process (mang)}
-- - KHAC hop dong Domino that TDUlties.updateChuyenXuLyHistory() ma FE doc
-- {user_creator, timeupdate "dd/MM/yyyy HH:mm:ss", user_process (chuoi ";")}. Code ghi da sua; migration
-- nay chuan hoa lai cac phan tu DA GHI SAI (nhan dien qua khoa nguoi_chuyen), giu nguyen thu tu va moi
-- phan tu dung chuan Domino. Idempotent: chay lai khong con phan tu nao co nguoi_chuyen.
-- Khong khoi phuc duoc unit_process/user_coordinate... cho du lieu cu (ban ghi sai chua tung luu).
UPDATE documents d
SET dynamic_fields = jsonb_set(
    d.dynamic_fields,
    '{chuyenxuly_obj}',
    (
        SELECT jsonb_agg(
            CASE
                WHEN e ? 'nguoi_chuyen' THEN jsonb_strip_nulls(
                    (e - 'nguoi_chuyen' - 'thoi_gian' - 'user_process')
                    || jsonb_build_object(
                        'user_creator', COALESCE(e->>'user_creator', e->>'nguoi_chuyen'),
                        'timeupdate', CASE
                            WHEN COALESCE(e->>'thoi_gian', '') ~ '^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}'
                                THEN to_char(left(e->>'thoi_gian', 19)::timestamp, 'DD/MM/YYYY HH24:MI:SS')
                            ELSE e->>'timeupdate'
                        END,
                        'user_process', CASE
                            WHEN jsonb_typeof(e->'user_process') = 'array'
                                THEN NULLIF((SELECT string_agg(x, ';') FROM jsonb_array_elements_text(e->'user_process') x), '')
                            ELSE e->>'user_process'
                        END
                    )
                )
                ELSE e
            END
            ORDER BY ord
        )
        FROM jsonb_array_elements(d.dynamic_fields->'chuyenxuly_obj') WITH ORDINALITY AS t(e, ord)
    )
)
WHERE jsonb_typeof(d.dynamic_fields->'chuyenxuly_obj') = 'array'
  AND jsonb_path_exists(d.dynamic_fields->'chuyenxuly_obj', '$[*] ? (exists(@.nguoi_chuyen))');
