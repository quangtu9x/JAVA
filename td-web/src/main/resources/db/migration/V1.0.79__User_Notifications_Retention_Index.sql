-- 27/10/2026 - job don hop thu thong bao (UserNotificationRetentionScheduler, hang dem): xoa mem thong bao DA DOC
-- qua app.realtime.inbox.retention-read-days (mac dinh 180 ngay) hoac MOI thong bao qua retention-all-days
-- (mac dinh 365 ngay). Index theo created_on cho dong chua xoa de job khong quet ca bang khi bang lon dan.
-- (Tach file rieng: V1.0.78 da giao nguoi dung, khong sua migration da co the da chay.)
CREATE INDEX IF NOT EXISTS IX_user_notifications_created_on
    ON user_notifications (created_on) WHERE deleted_on IS NULL;
