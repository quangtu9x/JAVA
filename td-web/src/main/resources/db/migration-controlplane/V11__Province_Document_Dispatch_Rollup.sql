-- Số liệu nơi nhận CUỘN SẴN theo từng văn bản đi (tính lúc đồng bộ — ProvinceIndexSyncService.refreshVbdiRollup).
-- Thẻ KPI/xếp hạng/lọc tình trạng của "Giám sát gửi văn bản đi" đọc thẳng các cột này thay vì nối 1,3 triệu nơi nhận
-- mỗi lần mở màn (đo thật trước đó: ~4 s khi không giới hạn thời gian). Xem docs/analysis/26 §6.
ALTER TABLE province_documents
    ADD COLUMN IF NOT EXISTS rx_total               INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS rx_waiting             INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS rx_arrived             INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS rx_processing          INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS rx_done                INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS rx_refused             INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS rx_revoked             INTEGER NOT NULL DEFAULT 0,
    -- Lượt gửi CŨ NHẤT còn "chưa đến" / còn "chưa tiếp nhận" (SENT|ARRIVED): so với NOW() - ngưỡng là biết văn bản
    -- có nơi nhận chậm hay không mà không cần đọc bảng nơi nhận.
    ADD COLUMN IF NOT EXISTS rx_oldest_waiting_on   TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS rx_oldest_pending_on   TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS rx_first_sent_on       TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS rx_last_event_on       TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS rx_accept_hours_sum    DOUBLE PRECISION NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS rx_accept_count        INTEGER NOT NULL DEFAULT 0;

-- Văn bản đi có nơi nhận (danh sách giám sát) theo ngày ban hành.
CREATE INDEX IF NOT EXISTS ix_province_documents_vbdi_dispatched
    ON province_documents (doc_date DESC, created_on DESC)
    WHERE deleted = FALSE AND doc_kind = 'VBDI' AND rx_total > 0;

-- Nơi nhận đang chờ (chưa đến/chưa tiếp nhận) — tập nhỏ, dùng cho đếm "chậm" + xếp hạng đơn vị nhận.
CREATE INDEX IF NOT EXISTS ix_province_vbdi_dispatches_pending
    ON province_vbdi_dispatches (vbdi_id, last_sent_on)
    WHERE state IN ('SENT', 'ARRIVED');
