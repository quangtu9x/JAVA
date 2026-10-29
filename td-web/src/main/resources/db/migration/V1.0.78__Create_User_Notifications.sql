-- 27/10/2026 - hop thu thong bao (chuong) cua nguoi dung, luu o DB DON VI (tenant). Yeu cau nguoi dung:
-- nguoi chua dang nhap van nhan lai duoc thong bao, truy xuat lai duoc lich su. Socket.io chi la "chuong
-- cua" (day ngay khi online); nguon su that la bang nay - FE keo lai khi dang nhap/ket noi lai.
-- Xem docs/architecture/realtime-socket-notifications.md.
--
-- 1 dong = 1 thong bao cho 1 nguoi nhan: username (recipient_type=user) hoac ma dinh danh don vi
-- (recipient_type=unit - hop thu chung cua van thu, 1 nguoi doc la ca don vi da doc). Cot tuong minh =
-- thu can loc/dem; noi dung hien thi (code, creator, opinion, deadline, message...) trong dynamic_fields.
CREATE TABLE user_notifications (
    id               UUID         PRIMARY KEY,
    recipient        VARCHAR(150) NOT NULL,
    recipient_type   VARCHAR(10)  NOT NULL DEFAULT 'user',
    database_type    VARCHAR(50),
    action           VARCHAR(100) NOT NULL,
    document_id      VARCHAR(100),
    read_on          TIMESTAMP,
    read_by          VARCHAR(100),
    dynamic_fields   JSONB        NOT NULL DEFAULT '{}'::jsonb,
    created_by       UUID,
    created_on       TIMESTAMP    NOT NULL DEFAULT NOW(),
    last_modified_by UUID,
    last_modified_on TIMESTAMP,
    deleted_on       TIMESTAMP,
    deleted_by       UUID,
    CONSTRAINT CK_user_notifications_recipient_type CHECK (recipient_type IN ('user', 'unit'))
);

CREATE INDEX IX_user_notifications_recipient
    ON user_notifications (lower(recipient), created_on DESC) WHERE deleted_on IS NULL;
CREATE INDEX IX_user_notifications_unread
    ON user_notifications (lower(recipient)) WHERE deleted_on IS NULL AND read_on IS NULL;
CREATE INDEX IX_user_notifications_document
    ON user_notifications (document_id) WHERE deleted_on IS NULL AND read_on IS NULL;

COMMENT ON TABLE user_notifications IS
    'Hop thu thong bao nguoi dung (chuong). An thong bao = xoa mem. Socket.io chi day khi online, FE keo lai tu bang nay.';
COMMENT ON COLUMN user_notifications.recipient IS 'username hoac ma dinh danh don vi (vd H56.07.01)';
COMMENT ON COLUMN user_notifications.action IS 'Hanh dong (chuyenxuly, chuyenkyduyet, tralai...) - khop ACTION_MAP FE notificationUtils.ts';
COMMENT ON COLUMN user_notifications.dynamic_fields IS 'Noi dung hien thi: code, creator, position, opinion, deadline, message';
