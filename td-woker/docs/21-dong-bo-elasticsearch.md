# 21 — Đồng bộ Elasticsearch (vận hành)

Thiết kế đầy đủ và lý do: `docs/architecture/13-dong-bo-elasticsearch.md` (thư mục docs gốc của HSCV).
Tài liệu này dành cho người triển khai và vận hành worker.

## 1. Tóm tắt

- td-web (Flyway `V1.0.72`) tạo bảng `search_index_queue` và trigger trên 7 bảng văn bản ở **mỗi DB tenant**.
  Mọi INSERT/UPDATE/DELETE đều sinh một dòng hàng đợi, trong cùng transaction.
- td-web `V1.0.73`: trigger gọi thêm `pg_notify('search_index', entity)`, chỉ gửi khi COMMIT.
- Worker (`com.td.worker.search`) **LISTEN** ở DB từng tenant (1 kết nối và 1 luồng nền `search-listen-<tenant>`
  mỗi tenant). Có thông báo thì xử lý **ngay** tenant đó, song song tối đa `max-parallel-tenants` tenant
  (luồng `search-sync-N`). Quét dự phòng mọi tenant mỗi `fallback-sweep-interval` (2 phút) để bù thông báo lỡ.
  Mỗi lượt xử lý một tenant, worker:
  1. Lấy lô hàng đợi (`FOR UPDATE SKIP LOCKED`).
  2. Đọc trạng thái hiện tại của từng dòng.
  3. Gửi bulk lên ES, version = id hàng đợi.
  4. Xoá dòng hàng đợi khi ES xác nhận.
- Index: alias `hscv-{tenant}-{entity}` trỏ tới index `hscv-{tenant}-{entity}-v{mappingVersion}`.

## 2. Triển khai lần đầu

1. Khởi động **td-web** (bản có migration V1.0.72). Kiểm tra trong DB tenant:
   `SELECT count(*) FROM search_index_queue;`. Bảng phải tồn tại và tăng dần khi có người dùng thao tác.
2. Khai cấu hình trong `td-woker/.env` (§3) rồi build lại và khởi động **worker**:
   `mvn package -DskipTests` rồi `java -jar target/td-worker.jar`. **Dừng** worker cũ trước khi build,
   vì ghi đè jar đang chạy có thể làm JVM lỗi.
3. Log phải có:
   - `Đồng bộ Elasticsearch đã chạy: uris=...`
   - với mỗi tenant/entity: `Elasticsearch: tạo mới index hscv-h05-vbdi-v1 ..., đưa N dòng vào hàng đợi để nạp lần đầu`.
4. Theo dõi tới khi hàng đợi về 0: `GET http://<worker>:8090/actuator/health`, mục `searchSync.queueSize`.
5. Đối chiếu: `curl http://10.10.10.116:9200/_cat/indices/hscv-*?v` so với số dòng từng bảng.

Không cần script nạp dữ liệu riêng. Khi ES bị dựng lại hoặc mất index, bước 3 tự chạy lại.

## 3. Cấu hình (`td-woker/.env`)

| Biến | Giá trị thật hiện tại | Ghi chú |
|---|---|---|
| `WORKER_SEARCH_SYNC_ENABLED` | `true` | |
| `ELASTICSEARCH_URIS` | `http://10.10.10.116:9200` | ES 8.19.6, không xác thực, cluster `prod-es` **dùng chung** |
| `ELASTICSEARCH_USERNAME` / `PASSWORD` / `API_KEY` | trống | Khai khi cluster bật bảo mật |
| `WORKER_SEARCH_SYNC_INDEX_PREFIX` | `hscv` | **Không** để trống hoặc trùng với hệ khác |
| `WORKER_SEARCH_SYNC_MAPPING_VERSION` | `1` | |
| `WORKER_SEARCH_SYNC_SHARDS` / `REPLICAS` | `1` / `0` | Tăng replicas khi cluster có từ 2 node |
| `WORKER_SEARCH_SYNC_NOTIFY_ENABLED` | `true` | LISTEN/NOTIFY; tắt thì chỉ còn quét dự phòng |
| `WORKER_SEARCH_SYNC_FALLBACK_SWEEP_INTERVAL` | `2m` | Quét dự phòng |
| `WORKER_SEARCH_SYNC_MAX_PARALLEL_TENANTS` | `4` | |
| `WORKER_SEARCH_SYNC_BATCH_SIZE` | `500` | |
| `WORKER_SEARCH_SYNC_RECONCILE_INTERVAL` | `6h` | `0` để tắt |
| `WORKER_SEARCH_SYNC_TENANTS` | trống | Trống = mọi tenant |
| `WORKER_SEARCH_SYNC_EXCLUDED_COLUMNS` | trống | |
| `WORKER_SEARCH_SYNC_KEYWORD_FIELDS` | danh sách mặc định (yml) | Khoá `dynamic_fields` dùng tìm **từ khoá** ở td-web (`kw.*`). Đổi danh sách thì tăng `MAPPING_VERSION` |

Danh sách đầy đủ (backoff, timeout, total fields, refresh, đổi tên bảng theo entity...) nằm ở khối
`worker.search-sync` trong `application.yml`. Mọi khoá đều có placeholder `${...}`, vì `.env` chỉ ghi đè
được những khoá có placeholder.

## 4. Theo dõi

- `/actuator/health` → `searchSync`:
  - `listeners` / `listenersConnected`: số tenant đang nghe / đang nối được. Hai số lệch nhau kéo dài thì kiểm tra kết nối DB tenant
    (`max_connections`, firewall). Trong lúc đó vẫn không mất dữ liệu, chỉ chậm tới lượt quét dự phòng.
  - `UP`: chu kỳ gần nhất không lỗi.
  - `DEGRADED`: có lỗi, xem `lastError`. Worker **vẫn** UP.
  - `UNKNOWN`: chưa cấu hình.
- `/actuator/prometheus`:
  - `worker_search_sync_queue_size`: tổng hàng đợi mọi tenant. Tăng mãi thì worker hoặc ES đang có vấn đề.
  - `worker_search_sync_queue_failing`: số dòng đang lỗi, phải về 0.
  - `worker_search_sync_indexed_total`, `worker_search_sync_deleted_total`, `worker_search_sync_failed_total`.

## 5. Xử lý sự cố

| Hiện tượng | Nguyên nhân / cách xử lý |
|---|---|
| Log `Elasticsearch chưa sẵn sàng, giữ nguyên hàng đợi...` | ES không kết nối được. Không mất dữ liệu: khi ES trở lại, worker tự bù |
| Log `LISTEN search_index tenant X mất kết nối/không mở được` | Kết nối LISTEN tới DB tenant đứt. Worker tự nối lại sau 10 giây và xử lý bù tenant đó. Lặp lại liên tục thì kiểm tra `max_connections` hoặc firewall cắt kết nối rảnh |
| `queue_failing > 0` | `SELECT entity_type, entity_id, attempts, last_error FROM search_index_queue WHERE attempts > 0;` Sửa nguyên nhân xong thì `UPDATE search_index_queue SET next_try_at = now() WHERE attempts > 0;` |
| `last_error` = `entity_type '...' chưa khai` | Có migration thêm trigger cho bảng mới nhưng worker chưa khai `WORKER_SEARCH_SYNC_TABLE_<ENTITY>` / `entities` |
| Log `bảng X không tồn tại ở tenant Y` | Tenant chưa chạy migration tạo bảng đó (td-web chưa khởi động lại) |
| `pointAlias` lỗi "index ... exists with the same name as the alias" | Có ai đó tạo index thường trùng tên alias. Xoá index đó, worker sẽ tạo lại và nạp lại |
| Muốn nạp lại một entity | `INSERT INTO search_index_queue (entity_type, entity_id, operation) SELECT 'vbdi', id, 'R' FROM vbdi_documents;` |
| Muốn dựng lại index từ đầu | Xoá `hscv-{tenant}-{entity}*` trên ES. Worker tự tạo và nạp lại |
| Đổi analyzer/mapping | Sửa `SearchDocumentMapper`, tăng `WORKER_SEARCH_SYNC_MAPPING_VERSION`, build rồi khởi động lại worker |

## 6. Test

- Unit test (chạy trong build thường): `SearchIndexNamingTest`, `SearchDocumentMapperTest`, `ElasticsearchHttpClientTest`.
- `SearchIndexQueueIntegrationTest`: chạy đúng migration V1.0.72 trên PostgreSQL. Có Docker thì dùng
  Testcontainers. Nếu không có Docker, khai `SEARCH_SYNC_IT_JDBC_URL/_USERNAME/_PASSWORD`: test chạy trong
  một schema tạm và xoá schema khi xong.
- `SearchIndexSyncEndToEndTest`: thêm `SEARCH_SYNC_IT_ES_URIS`. Test chạy toàn bộ vòng đời trên ES thật
  với tiền tố index ngẫu nhiên `hscvit*`, xoá index khi xong. Các bước được kiểm tra:
  - nạp lần đầu,
  - tìm không dấu,
  - lọc theo jsonb,
  - sửa/xoá,
  - chống ghi đè bản cũ,
  - đổi mapping version,
  - đối soát.
- `SearchSyncCoordinatorTest`: gộp yêu cầu trùng, 1 luồng mỗi tenant, chạy song song, xếp lại khi còn việc, hẹn giờ thử lại.
- `SearchIndexQueueIntegrationTest` còn kiểm tra NOTIFY: chỉ đến sau commit, rollback thì không, nhiều dòng gộp 1.
- `SearchIndexSyncEndToEndTest.notifyDriven...`: LISTEN thật, không quét định kỳ; đo độ trễ commit tới ES.
- Đã chạy thật ngày 17/10/2026 (DB `tdworker` schema tạm + ES 10.10.10.116): 72/72 đạt, độ trễ 325 ms.

## 7. Trường cho tìm kiếm nâng cao td-web (17/10/2026)

Ngoài nguyên dòng DB, mỗi document có thêm `norm_dyn`, `norm_col`, `date_dyn`, `date_col`, `kw.*`, `sort_promulgation_date`, `sort_document_number`. Ý nghĩa từng trường xem `docs/architecture/13` §6. td-web (`ElasticsearchAdvancedSearchIndex`) truy vấn đúng các trường này.

- `SearchIndexSyncEndToEndTest#advancedSearchQueryContract` là **hợp đồng** với td-web, chạy trên ES thật. Đổi tên hoặc kiểu trường thì sửa cả td-web, rồi tăng `WORKER_SEARCH_SYNC_MAPPING_VERSION`.
- td-web chỉ đọc index khi worker đã nạp xong lần đầu, tức không còn dòng `operation='R'` của entity trong hàng đợi. Trong lúc nạp, tìm kiếm tự chạy SQL.

## 8. Làn trích nội dung tệp (17/10/2026)

Coordinator thứ hai, chạy trên luồng riêng, xử lý dòng hàng đợi `entity_type='file'` (trigger `file_metadata`, migration V1.0.74). Làn này dùng Apache Tika (chưa OCR: PDF scan → `EMPTY`, ảnh bỏ qua), ghi kết quả vào `file_content_extracts`, rồi đưa văn bản cha vào hàng đợi để nạp `files_text`. Cấu hình `WORKER_FILE_EXTRACT_*` (cần `MINIO_ENABLED=true`). Toàn bộ thiết kế, cấu hình và vận hành: `docs/architecture/14-trich-noi-dung-tep.md` (thư mục docs gốc HSCV).
