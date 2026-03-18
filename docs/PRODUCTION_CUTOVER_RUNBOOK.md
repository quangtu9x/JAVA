# Production Cutover Runbook

## 1) Scope

Runbook nay dung cho cutover he thong quan ly van ban tu Domino write path sang kien truc moi:

- Document domain (PostgreSQL + MinIO metadata)
- Workflow domain (PostgreSQL definition + runtime + history)
- Audit log domain (PostgreSQL append-only)
- Async integration (outbox -> Redis/WebSocket/Elasticsearch workers)

## 2) Roles and contacts

- Release commander: Dieu phoi chung, quyet dinh go/no-go.
- Backend lead: API, migration, outbox workers.
- DevOps lead: deployment, monitoring, rollback infra.
- Security lead: Keycloak role/permission validation.
- Business owner: xac nhan nghiep vu va ky duyet cutover.

## 3) Pre-cutover checklist (T-7 day to T-1 day)

1. Freeze thay doi schema va API contract.
2. Confirm da deploy migration V1.0.4 va V1.0.5 tren staging.
3. Confirm canary test pass voi nhom user dai dien.
4. Confirm mismatch report Domino vs he thong moi < 1% trong 2 tuan gan nhat.
5. Confirm dashboard/alert da san sang:
   - API latency, error rate
   - DB health and connection pool
   - Outbox pending count and max age
   - WebSocket delivery rate
   - Elasticsearch indexing lag
6. Confirm backup/restore drill da duoc test:
   - PostgreSQL PITR
   - MinIO object backup
   - Keycloak export
7. Confirm rollback plan da duoc review va phe duyet.

## 4) Change window preparation (T-2h)

1. Open war-room channel va ghi lai timeline.
2. Stop scheduled jobs khong quan trong.
3. Enable enhanced logging:
   - request_id, correlation_id, trace_id
4. Snapshot backup:
   - PostgreSQL full backup
   - MinIO bucket snapshot
5. Capture baseline metrics (truoc cutover):
   - P95/P99 latency
   - QPS
   - error rate
   - backlog outbox

## 5) Cutover steps (T0)

1. Announce maintenance mode (neu can).
2. Deploy app version co Workflow API va audit pipeline moi.
3. Apply DB migrations neu chua apply tren prod:
   - V1.0.4__Create_Workflow_And_Audit_Tables.sql
   - V1.0.5__Seed_Default_Workflow_Definitions.sql
4. Enable feature flags theo thu tu:
   - flag.workflow.submit = ON
   - flag.workflow.transition = ON
   - flag.audit.events = ON
   - flag.domino.write = OFF
5. Route write traffic 100% sang API moi.
6. Keep Domino read-only path for lookup during grace period.

## 6) Immediate validation (T0 + 15m)

1. Smoke test API:
   - create document
   - submit workflow
   - approve/reject action
   - task inbox query
   - audit search by correlation_id
2. Data validation:
   - documents.status_source = WORKFLOW cho luong moi
   - workflow_instances duoc tao
   - workflow_history co ban ghi transition
   - audit_events co ban ghi cho thao tac vua thuc hien
3. Integration validation:
   - outbox pending khong tang vo han
   - WebSocket push co thong bao transition
   - Elasticsearch index update thanh cong

## 7) Stabilization checks (T0 + 1h to T0 + 24h)

1. Theo doi KPIs moi 15 phut trong 2 gio dau:
   - 5xx rate < nguong canh bao
   - p95 latency khong tang > 20% baseline
   - outbox oldest pending age < 5 phut
2. Theo doi mismatch report Domino read vs he thong moi.
3. Theo doi business feedback tu user canary va key users.

## 8) Rollback criteria

Rollback ngay neu mot trong cac dieu kien sau xay ra:

1. Error rate tang vuot nguong SLA va khong giam sau 15 phut.
2. Outbox ket backlog > nguong va oldest pending age > 30 phut.
3. Transition workflow sai nghiep vu tren nhieu request.
4. Audit events mat chuoi truy vet request/correlation.

## 9) Rollback procedure

1. Set feature flags:
   - flag.workflow.transition = OFF
   - flag.workflow.submit = OFF
   - flag.domino.write = ON
2. Route write traffic ve Domino servlet.
3. Disable async workers neu gay tac nghe.
4. Restore DB chi khi co hu hong du lieu (theo quyet dinh release commander).
5. Publish incident summary va status toan bo stakeholder.

## 10) Post-cutover tasks (T+1 day to T+7 day)

1. Chot bao cao cutover:
   - timeline
   - issue list
   - actions da lam
2. Keep dual-read validation it nhat 7 ngay.
3. Tao backlog fix cho mismatch/edge cases.
4. Chot ngay decommission Domino write path hoan toan.

## 11) Evidence checklist for audit

1. Deployment logs
2. Migration logs
3. Smoke test results
4. KPI dashboard snapshots
5. Incident/rollback notes (neu co)
6. Approval records from business owner and release commander
