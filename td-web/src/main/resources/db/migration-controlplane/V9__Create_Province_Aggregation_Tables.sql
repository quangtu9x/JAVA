-- Tổng hợp toàn tỉnh (route FE /tonghop): kho dữ liệu TẬP TRUNG dùng cho "Tìm kiếm văn bản toàn tỉnh",
-- "Tổng hợp chung" (thống kê theo đơn vị) và "Giám sát gửi văn bản đi tập trung".
--
-- Thay thế 3 kho rời rạc của Domino cũ (Redis blob ThongKeTongHopVanBan* do job ngoài ghi, Elasticsearch
-- data_* nạp qua mail-in elastic.nsf, nhatkyhethong.nsf hard-code IP) bằng 4 bảng trong control-plane
-- (DB dùng chung mọi tenant — đúng chỗ cho dữ liệu XUYÊN tenant). Dữ liệu gốc vẫn nằm ở DB từng tenant;
-- các bảng dưới đây là BẢN CHIẾU (projection) chỉ đọc, được ProvinceIndexSyncService đồng bộ tăng dần
-- (watermark) + đồng bộ toàn bộ định kỳ — có thể xoá trắng và dựng lại bất kỳ lúc nào.

CREATE TABLE IF NOT EXISTS province_documents (
    tenant_id           VARCHAR(100) NOT NULL,
    document_id         UUID         NOT NULL,
    doc_kind            VARCHAR(10)  NOT NULL,           -- VBDEN | VBDI
    owner_identifier    VARCHAR(200),                    -- đơn vị sở hữu (vbden: đơn vị nhận; vbdi: đơn vị phát hành)
    owner_name          VARCHAR(500),
    code                VARCHAR(300),                    -- số/ký hiệu
    title               TEXT,                            -- trích yếu
    document_type_name  VARCHAR(300),
    agency_identifier   VARCHAR(200),                    -- vbden: đơn vị gửi (sender_identifier)
    agency_name         VARCHAR(500),                    -- vbden: cơ quan ban hành; vbdi: đơn vị phát hành
    signer              VARCHAR(300),
    priority            VARCHAR(100),
    doc_date            DATE,                            -- vbden: ngày đến; vbdi: ngày ban hành
    promulgation_date   DATE,
    deadline_date       DATE,
    status_code         VARCHAR(50),
    process_state       VARCHAR(30),                     -- vbden: CHO_VAO_SO/CHUA_XU_LY/DANG_XU_LY/DA_XU_LY/TU_CHOI; vbdi: CHUA_GUI/DA_GUI
    is_overdue          BOOLEAN      NOT NULL DEFAULT FALSE,
    is_internal         BOOLEAN      NOT NULL DEFAULT FALSE,
    is_network          BOOLEAN      NOT NULL DEFAULT FALSE,   -- liên thông trong tỉnh (qua mạng)
    is_national         BOOLEAN      NOT NULL DEFAULT FALSE,   -- trục liên thông quốc gia
    is_paper            BOOLEAN      NOT NULL DEFAULT FALSE,
    is_signed           BOOLEAN      NOT NULL DEFAULT FALSE,
    is_revoked          BOOLEAN      NOT NULL DEFAULT FALSE,
    source_send_log_id  VARCHAR(100),                    -- vbden nhận qua mạng: id dòng nhật ký gửi phía đơn vị gửi
    search_text         TEXT,                            -- chữ thường, bỏ dấu: số ký hiệu + trích yếu + cơ quan + người ký
    created_on          TIMESTAMP WITH TIME ZONE,
    source_modified_on  TIMESTAMP WITH TIME ZONE,
    deleted             BOOLEAN      NOT NULL DEFAULT FALSE,
    synced_on           TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    PRIMARY KEY (tenant_id, document_id)
);

CREATE INDEX IF NOT EXISTS ix_province_documents_kind_date
    ON province_documents (doc_kind, doc_date DESC) WHERE deleted = FALSE;
CREATE INDEX IF NOT EXISTS ix_province_documents_owner
    ON province_documents (owner_identifier varchar_pattern_ops) WHERE deleted = FALSE;
CREATE INDEX IF NOT EXISTS ix_province_documents_created
    ON province_documents (created_on DESC) WHERE deleted = FALSE;

CREATE TABLE IF NOT EXISTS province_vbdi_dispatches (
    tenant_id            VARCHAR(100) NOT NULL,
    vbdi_id              UUID         NOT NULL,
    recipient_key        VARCHAR(600) NOT NULL,          -- recipient_group|identifier (hoặc tên khi không có mã)
    recipient_group      VARCHAR(50)  NOT NULL,          -- internal_unit | contact_unit | external_unit
    recipient_identifier VARCHAR(200),
    recipient_name       VARCHAR(500),
    sender_identifier    VARCHAR(200),
    state                VARCHAR(20)  NOT NULL,          -- SENT/ARRIVED/ACCEPTED/ASSIGNED/PROCESSING/DONE/REFUSED/REVOKED
    state_label          VARCHAR(200),
    send_count           INTEGER      NOT NULL DEFAULT 0,
    first_sent_on        TIMESTAMP WITH TIME ZONE,
    last_sent_on         TIMESTAMP WITH TIME ZONE,
    arrived_on           TIMESTAMP WITH TIME ZONE,
    accepted_on          TIMESTAMP WITH TIME ZONE,
    completed_on         TIMESTAMP WITH TIME ZONE,
    last_event_on        TIMESTAMP WITH TIME ZONE,
    synced_on            TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    PRIMARY KEY (tenant_id, vbdi_id, recipient_key)
);

CREATE INDEX IF NOT EXISTS ix_province_vbdi_dispatches_recipient
    ON province_vbdi_dispatches (recipient_identifier varchar_pattern_ops);
CREATE INDEX IF NOT EXISTS ix_province_vbdi_dispatches_state
    ON province_vbdi_dispatches (state, last_sent_on);

CREATE TABLE IF NOT EXISTS province_vbdi_dispatch_events (
    tenant_id       VARCHAR(100) NOT NULL,
    log_id          UUID         NOT NULL,               -- id dòng vbdi_send_log_documents gốc
    vbdi_id         UUID         NOT NULL,
    recipient_key   VARCHAR(600) NOT NULL,
    send_type       VARCHAR(50),
    status          VARCHAR(200),
    content         TEXT,
    user_creator    VARCHAR(300),
    created_on      TIMESTAMP WITH TIME ZONE,
    PRIMARY KEY (tenant_id, log_id)
);

CREATE INDEX IF NOT EXISTS ix_province_vbdi_dispatch_events_doc
    ON province_vbdi_dispatch_events (tenant_id, vbdi_id, created_on);

CREATE TABLE IF NOT EXISTS province_sync_state (
    tenant_id          VARCHAR(100) PRIMARY KEY,
    docs_watermark     TIMESTAMP WITH TIME ZONE,
    logs_watermark     TIMESTAMP WITH TIME ZONE,
    last_run_on        TIMESTAMP WITH TIME ZONE,
    last_success_on    TIMESTAMP WITH TIME ZONE,
    last_full_sync_on  TIMESTAMP WITH TIME ZONE,
    last_error         TEXT,
    last_duration_ms   BIGINT,
    last_changed_rows  INTEGER
);

-- Tìm kiếm chuỗi con (LIKE '%...%') trên search_text: dùng trigram nếu server cho phép tạo extension.
-- Không có quyền tạo extension thì bỏ qua — truy vấn vẫn đúng, chỉ chậm hơn (quét tuần tự).
DO $$
BEGIN
    BEGIN
        CREATE EXTENSION IF NOT EXISTS pg_trgm;
    EXCEPTION WHEN OTHERS THEN
        RAISE NOTICE 'Khong tao duoc extension pg_trgm (%), bo qua chi muc trigram', SQLERRM;
    END;
    IF EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'pg_trgm') THEN
        EXECUTE 'CREATE INDEX IF NOT EXISTS ix_province_documents_search_trgm '
             || 'ON province_documents USING gin (search_text gin_trgm_ops)';
    END IF;
END $$;
