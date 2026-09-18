-- BL-117 (bổ sung 19/08/2026): registry "Máy chủ CSDL" (host+port Postgres) — CHỈ để tiện chọn nhanh
-- lúc khởi tạo đơn vị (bảng tenants) thay vì gõ tay mỗi lần. KHÔNG có FK tới tenants (xem javadoc
-- DbServer.java) — đổi/xoá 1 dòng ở đây không ảnh hưởng tenant đã tạo trước đó.

CREATE TABLE db_servers (
    id                  UUID         PRIMARY KEY DEFAULT uuid_generate_v4(),
    name                VARCHAR(200) NOT NULL,
    host                VARCHAR(255) NOT NULL,
    port                INT          NOT NULL,
    description         VARCHAR(1000),
    active              BOOLEAN      NOT NULL DEFAULT TRUE,
    created_by          UUID,
    created_on          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    last_modified_by    UUID,
    last_modified_on    TIMESTAMP WITH TIME ZONE,
    deleted_on          TIMESTAMP WITH TIME ZONE,
    deleted_by          UUID
);

CREATE UNIQUE INDEX UX_db_servers_host_port ON db_servers(host, port) WHERE deleted_on IS NULL;
CREATE INDEX IX_db_servers_active          ON db_servers(active);
CREATE INDEX IX_db_servers_deleted_on      ON db_servers(deleted_on);

COMMENT ON TABLE  db_servers             IS 'Registry may chu Postgres (host+port) - chi de goi y khi khoi tao don vi (tenant), khong co FK toi tenants';
COMMENT ON COLUMN db_servers.active      IS 'false = tam an khoi danh sach chon luc khoi tao don vi (vd dang bao tri), khac han xoa mem (deleted_on)';
