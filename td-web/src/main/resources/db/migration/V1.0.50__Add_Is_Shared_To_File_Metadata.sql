-- Yêu cầu người dùng 08/09/2026 ("điều chỉnh thêm dấu hiệu nếu là văn bản nhận được bằng chức năng
-- 'chuyển xử lý' thì không cho xóa đính kèm gốc"): bản sao "văn bản đến" tạo bởi vbden.forward_unit
-- (VbdenForwardUnitTaskHandler) KHÔNG copy MinIO — mỗi dòng file_metadata của nó trỏ THẲNG cùng
-- object_key/bucket_name với văn bản gốc ("đọc chung" theo đúng yêu cầu trước đó, xem
-- td-woker/docs/15-vbden-forward-unit-reference.md §4). DeleteFileUseCaseImpl xóa file lại gọi
-- MinioService#removeObject VÔ ĐIỀU KIỆN trước khi xóa dòng DB (xem DeleteFileUseCaseImpl.java) -
-- nếu đơn vị nhận (tenant đích) xóa dòng "dùng chung" này, object vật lý bị xóa thật, kéo theo văn
-- bản GỐC ở tenant khác cũng mất tệp - lỗi nghiêm trọng, im lặng, xảy ra ở tenant khác hẳn nơi bấm
-- xóa. Thêm cờ is_shared để DeleteFileUseCaseImpl chặn xóa các dòng này hẳn (không chỉ bỏ qua bước
-- xóa vật lý - chặn toàn bộ, đúng yêu cầu "không cho xóa đính kèm gốc").
ALTER TABLE file_metadata ADD COLUMN is_shared BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN file_metadata.is_shared IS 'TRUE = dòng này CHỈ tham chiếu (không sở hữu) object MinIO tại file_path/bucket_name - vd bản sao vbden.forward_unit "đọc chung" tệp của văn bản gốc ở tenant khác. DeleteFileUseCaseImpl phải CHẶN xóa (cả DB lẫn MinIO) khi cờ này TRUE, tránh xóa nhầm object vật lý mà tenant/văn bản khác vẫn đang tham chiếu.';
