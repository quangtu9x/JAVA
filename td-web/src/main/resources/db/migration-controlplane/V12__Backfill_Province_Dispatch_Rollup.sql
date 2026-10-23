-- Bù số liệu cuộn sẵn rx_* (V11) cho văn bản đi đã có trong kho: V11 chỉ THÊM cột (mặc định 0), còn việc tính do
-- đồng bộ đảm nhận — nhưng đồng bộ tăng dần chỉ tính lại văn bản có thay đổi, nên văn bản cũ giữ rx_total = 0 và
-- biến mất khỏi màn Giám sát (lọc rx_total > 0) cho tới lần đồng bộ toàn bộ kế tiếp. Câu dưới = đúng câu
-- ProvinceIndexSyncService.refreshVbdiChannels, chạy cho mọi tenant.
UPDATE province_documents d SET is_internal = c.i, is_network = c.n, is_national = c.q,
  rx_total = c.total, rx_waiting = c.waiting, rx_arrived = c.arrived, rx_processing = c.processing,
  rx_done = c.done, rx_refused = c.refused, rx_revoked = c.revoked,
  rx_oldest_waiting_on = c.oldest_waiting, rx_oldest_pending_on = c.oldest_pending,
  rx_first_sent_on = c.first_sent, rx_last_event_on = c.last_event,
  rx_accept_hours_sum = c.accept_sum, rx_accept_count = c.accept_n
FROM (
  SELECT p.tenant_id, p.document_id,
    COALESCE(BOOL_OR(x.recipient_group = 'internal_unit'), FALSE) AS i,
    COALESCE(BOOL_OR(x.recipient_group = 'contact_unit'), FALSE) AS n,
    COALESCE(BOOL_OR(x.recipient_group = 'external_unit'), FALSE) AS q,
    COUNT(x.vbdi_id)::int AS total,
    COUNT(*) FILTER (WHERE x.state = 'SENT')::int AS waiting,
    COUNT(*) FILTER (WHERE x.state = 'ARRIVED')::int AS arrived,
    COUNT(*) FILTER (WHERE x.state IN ('ACCEPTED', 'ASSIGNED', 'PROCESSING'))::int AS processing,
    COUNT(*) FILTER (WHERE x.state = 'DONE')::int AS done,
    COUNT(*) FILTER (WHERE x.state = 'REFUSED')::int AS refused,
    COUNT(*) FILTER (WHERE x.state = 'REVOKED')::int AS revoked,
    MIN(x.last_sent_on) FILTER (WHERE x.state = 'SENT') AS oldest_waiting,
    MIN(x.last_sent_on) FILTER (WHERE x.state IN ('SENT', 'ARRIVED')) AS oldest_pending,
    MIN(x.first_sent_on) AS first_sent,
    MAX(x.last_event_on) AS last_event,
    COALESCE(SUM(EXTRACT(EPOCH FROM (x.accepted_on - x.last_sent_on))::double precision / 3600.0)
      FILTER (WHERE x.accepted_on >= x.last_sent_on), 0) AS accept_sum,
    COUNT(*) FILTER (WHERE x.accepted_on >= x.last_sent_on)::int AS accept_n
  FROM province_documents p
  LEFT JOIN province_vbdi_dispatches x ON x.tenant_id = p.tenant_id AND x.vbdi_id = p.document_id
  WHERE p.doc_kind = 'VBDI'
  GROUP BY p.tenant_id, p.document_id
) c
WHERE d.tenant_id = c.tenant_id AND d.document_id = c.document_id
  AND (d.is_internal, d.is_network, d.is_national, d.rx_total, d.rx_waiting, d.rx_arrived, d.rx_processing,
       d.rx_done, d.rx_refused, d.rx_revoked, d.rx_oldest_waiting_on, d.rx_oldest_pending_on,
       d.rx_first_sent_on, d.rx_last_event_on, d.rx_accept_hours_sum, d.rx_accept_count)
    IS DISTINCT FROM
      (c.i, c.n, c.q, c.total, c.waiting, c.arrived, c.processing, c.done, c.refused, c.revoked,
       c.oldest_waiting, c.oldest_pending, c.first_sent, c.last_event, c.accept_sum, c.accept_n)
;

-- Vô hiệu bộ nhớ đệm kết quả (khoá theo MAX(last_data_change_on)).
UPDATE province_sync_state SET last_data_change_on = NOW();
