package com.td.application.realtime;

/**
 * Cổng đẩy thông báo tới server socket.io (implementation ở {@code td-infrastructure}).
 *
 * <p>Hợp đồng: KHÔNG chặn luồng gọi và KHÔNG ném lỗi — thông báo là phụ, mất thông báo không được
 * làm hỏng nghiệp vụ (giống Domino {@code sendSocketNotSave} nuốt mọi lỗi).</p>
 */
public interface RealtimeNotificationGateway {

    void publish(RealtimeNotification notification);
}
