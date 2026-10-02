-- Yeu cau nguoi dung 25/09/2026 ("chuc nang sua file o OnlyOffice -> toi muon luu vet va FILE MOI, khong
-- luu de vao file cu, co the tham khao chuc nang sua file cu (bte)"): truoc day callback OnlyOffice GHI DE
-- noi dung tep goc (cung file_metadata.id). Nay moi lan sua tao 1 TEP MOI (dong file_metadata moi), tep goc
-- giu nguyen - dung tinh than luong "Sua tep" BTE (tdeditorJavaFile.js: upload tep moi, giu ban goc).
--
-- source_file_id  : tep GOC ma tep nay duoc sua ra (NULL = tep tai len binh thuong).
-- edit_session_key: key phien soan thao OnlyOffice (document.key) da tao ra tep nay - Document Server co
--                   the goi callback NHIEU lan trong 1 phien (force-save status 6, roi status 2 khi dong):
--                   cac lan sau GHI VAO DUNG tep moi cua phien do thay vi tao them tep trung lap; FE dung
--                   (source_file_id, edit_session_key) de biet phien vua dong da luu ra tep nao.
ALTER TABLE file_metadata ADD COLUMN source_file_id UUID;
ALTER TABLE file_metadata ADD COLUMN edit_session_key VARCHAR(200);

CREATE INDEX IX_file_metadata_source_file ON file_metadata(source_file_id) WHERE source_file_id IS NOT NULL;

COMMENT ON COLUMN file_metadata.source_file_id IS 'Tep goc ma tep nay duoc sua ra (vd sua truc tuyen OnlyOffice) - NULL voi tep tai len binh thuong.';
COMMENT ON COLUMN file_metadata.edit_session_key IS 'Key phien soan thao (OnlyOffice document.key) da tao ra tep nay - de cac lan luu sau trong CUNG phien ghi vao dung tep, khong tao trung.';
