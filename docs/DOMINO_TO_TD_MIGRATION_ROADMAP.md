# Roadmap chuyen doi tu Domino sang Document + Workflow + Audit Log

Muc tieu: chuyen doi an toan tu he thong Domino (data + servlet API) sang kien truc moi ma khong gian doan nghiep vu.

## 1) Nguyen tac migration

- Khong big-bang. Di theo pha, co canary va rollback.
- Uu tien tuong thich API trong giai doan dau.
- Song song ghi log va doi soat so lieu giua 2 he thong.
- Workflow la nguon status chinh sau khi cutover.

## 2) Mo hinh target

- Document domain: PostgreSQL + MinIO metadata.
- Workflow domain: PostgreSQL (definition + runtime + history).
- Audit log domain: PostgreSQL append-only (audit_events), co the stream tiep sang kho phan tich.
- Search/read optimization: Elasticsearch.
- Low-latency cache/event: Redis.
- Real-time client update: WebSocket.
- IAM: Keycloak.

## 3) Ke hoach theo giai doan

## Phase 0 - Khao sat va mapping (1-2 tuan)

Viec can lam:

1. Chot inventory form/agent/view trong Domino.
2. Mapping schema Domino -> documents + attributes_json.
3. Mapping status Domino -> business status chuan.
4. Mapping actor/nhom xu ly Domino -> role/group tu Keycloak.
5. Chot danh sach API servlet dang duoc tich hop boi cac he thong khac.

Deliverable:

- Bang mapping du lieu va status.
- Danh sach API phai backward-compatible.
- Danh muc rui ro va muc do uu tien.

Gate qua phase:

- 100% loai van ban quan trong da co mapping.

## Phase 1 - Foundation schema + contract (1-2 tuan)

Viec can lam:

1. Apply migration V1.0.4 de tao workflow/audit/outbox.
2. Tao service skeleton cho workflow va audit APIs.
3. Chuan hoa header request: X-Request-Id, X-Correlation-Id.
4. Tao outbox worker de publish event.
5. Bat audit event cho API documents hien tai.

Deliverable:

- DB schema moi tren moi env.
- API contract draft hoan chinh.
- Dashboard theo doi outbox backlog.

Gate qua phase:

- Co the tao document + ghi audit + tao workflow instance test tren env dev.

## Phase 2 - Dual write + read compare (2-4 tuan)

Viec can lam:

1. Adapter de goi Domino servlet (read) song song voi API moi.
2. Khi tao/cap nhat document tren luong moi, ghi them mapping id Domino <-> id moi.
3. Build job doi soat nightly:
   - So luong ban ghi
   - Status
   - Truong bat buoc
4. Canary theo don vi (vd 5-10% user).

Deliverable:

- Bao cao mismatch hang ngay.
- Co che retry + dead-letter cho event fail.

Gate qua phase:

- Ty le mismatch < 1% trong 2 tuan lien tiep.

## Phase 3 - Workflow ownership cutover (2-3 tuan)

Viec can lam:

1. Khoa cap nhat status truc tiep o Document API cho luong da bat workflow.
2. Bat endpoints submit/approve/reject/return.
3. Bat task inbox theo user/group (Keycloak roles).
4. Bat WebSocket push khi workflow transition.

Deliverable:

- Trang thai van ban do workflow quan ly.
- Frontend hien timeline xu ly theo workflow_history.

Gate qua phase:

- 100% transition cua nhom canary di qua Workflow API.

## Phase 4 - Audit cutover + compliance (1-2 tuan)

Viec can lam:

1. Toan bo thao tac quan trong ghi vao audit_events.
2. Truy van audit theo object/request/correlation.
3. Chot retention va policy export bao cao.
4. Chot role xem audit (thuong chi ADMIN/AUDITOR).

Deliverable:

- Bao cao audit day du theo mau nghiep vu.
- Kiem tra truy vet tu request -> workflow -> document -> audit.

Gate qua phase:

- Dat yeu cau kiem toan noi bo.

## Phase 5 - Decommission Domino write path (1-2 tuan)

Viec can lam:

1. Chuyen servlet Domino sang read-only (neu can giu tra cuu lich su cu).
2. Chuyen toan bo write sang API moi.
3. Tat dan cac endpoint Domino khong con duoc goi.
4. Cap nhat tai lieu tich hop cho he thong phu thuoc.

Deliverable:

- He thong moi la write path duy nhat.
- Domino con vai tro archive/legacy read (co thoi han).

Gate hoan tat:

- Khong con luong nghiep vu ghi vao Domino trong 30 ngay.

## 4) Rollback strategy

Moi phase deu can 1 nut rollback ro rang:

1. Feature flag cho submit/approve/reject theo nhom user.
2. Co che chuyen read ve Domino neu search/index co su co.
3. Event publish fail khong lam mat du lieu goc (outbox retry).
4. Backup DB truoc moi dot cutover.

## 5) KPI de theo doi migration

- P95 API latency cho documents/workflows.
- Ty le loi transition workflow.
- Outbox pending age (phut).
- Ty le mismatch doi soat Domino vs he thong moi.
- Ty le su kien audit bi thieu request_id/correlation_id.

## 6) RACI toi thieu

- Business owner: chot mapping status va quy trinh duyet.
- Backend team: APIs, workflow engine, outbox, migration scripts.
- Frontend team: submit/task inbox/timeline/websocket.
- DevOps: monitoring, scaling, backup/restore drill.
- Security team: role model Keycloak, policy truy cap audit.

## 7) Ke hoach ngat ket noi Domino de xuat

1. T0: Dong bang thay doi schema Domino.
2. T0 + 1 tuan: Read-only toan bo API Domino.
3. T0 + 4 tuan: Dung adapter Domino cho luong online.
4. T0 + 8 tuan: Chot archive, chi cho phep truy cap boi nhom van hanh.
