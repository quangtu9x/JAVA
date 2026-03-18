# Thiet ke schema: Document + Workflow + Audit Log

## 1) Muc tieu

He thong quan ly van ban can dam bao 3 truc du lieu ton tai doc lap:

- Document domain: du lieu nghiep vu cua van ban.
- Workflow domain: dieu phoi quy trinh va trang thai xu ly.
- Audit log domain: lich su bat bien de truy vet/compliance.

Nguyen tac bat buoc:

- Document khong tu quyet dinh status nghiep vu khi da gan workflow.
- Workflow la nguon su that cua transition va current status.
- Audit log append-only, khong update, khong delete theo luong nghiep vu.

## 2) Bounded context va ownership

### Document domain

- Bang chinh: documents (dang ton tai).
- Thuoc tinh cot loi: title, document_type, content, tags_json, attributes_json, metadata_json, version_no.
- Thuoc tinh bo sung: workflow_instance_id, status_source, submitted_on, approved_on, rejected_on.

Ownership:

- Quan ly metadata van ban.
- Quan ly file lien quan qua file_metadata/MinIO.
- Cung cap read model cho tra cuu.

### Workflow domain

- Definition layer:
  - workflow_definitions
  - workflow_definition_steps
  - workflow_definition_transitions
- Runtime layer:
  - workflow_instances
  - workflow_tasks
  - workflow_history

Ownership:

- Quy dinh luong transition hop le.
- Quan ly task duyet/phe duyet/tu choi/chuyen buoc.
- Dong bo current status ve document read model.

### Audit log domain

- Bang chinh: audit_events (append-only).

Ownership:

- Ghi nhan ai lam gi, khi nao, tren doi tuong nao, truoc/sau thay doi.
- Truy van lich su theo object, actor, correlation_id, trace_id.

### Integration reliability

- Bang outbox_events de bao dam publish event tin cay (outbox pattern).

## 3) Muc du lieu va rang buoc quan trong

## 3.1 Document table (bo sung)

- workflow_instance_id: lien ket active workflow cua van ban.
- status_source: MANUAL | WORKFLOW.
- submitted_on, approved_on, rejected_on: timestamp support bao cao.

Rule:

- Neu status_source = WORKFLOW thi chi Workflow Service/API duoc cap nhat status.
- API update document thong thuong khong duoc nhay status truc tiep.

## 3.2 Workflow definition

workflow_definitions:

- workflow_code + version_no unique.
- is_active de chot version dang su dung.

workflow_definition_steps:

- unique(definition_id, step_code)
- unique(definition_id, step_order)

workflow_definition_transitions:

- unique(definition_id, from_step_id, action_code)
- required_roles_json cho policy role-based.

## 3.3 Workflow runtime

workflow_instances:

- entity_type + entity_id map ve document.
- current_status va current_step_id la trang thai hien tai.
- partial unique index dam bao moi entity chi co 1 workflow dang active.

workflow_tasks:

- task_status: PENDING | IN_PROGRESS | DONE | REJECTED | CANCELED.
- assignee_user_id / assignee_group_code phuc vu inbox task.

workflow_history:

- bat bien theo tung transition.
- luu action_code, actor, status old/new, request/correlation id.

## 3.4 Audit log

audit_events:

- action, result, object_type, object_id la bo truong bat buoc.
- before_data_json, after_data_json, delta_json de audit chi tiet.
- source_service, source_module, request_id, correlation_id, trace_id de tracing lien service.

Rule:

- Khong cap nhat ban ghi audit sau khi insert.
- Chi purge theo retention policy va theo quy dinh phap ly/noi bo.

## 3.5 Outbox

outbox_events:

- status: PENDING | PUBLISHED | FAILED.
- retry_count, next_retry_on ho tro retry co kiem soat.

Su dung:

- Publish sang Redis pub/sub, WebSocket notifier, Elasticsearch indexer.

## 4) Danh muc status de chuan hoa

Khuyen nghi chia 2 lop status:

- Workflow status (cho runtime): CREATED, ACTIVE, COMPLETED, CANCELED.
- Business status (hien thi cho van ban): DRAFT, SUBMITTED, IN_REVIEW, APPROVED, REJECTED, RETURNED, ARCHIVED.

Rule map:

- Document.status luu business status.
- Workflow.current_status luu workflow status.

## 5) Chien luoc index va hieu nang

Index uu tien:

- documents(status, document_type, last_modified_on)
- workflow_instances(entity_type, entity_id)
- workflow_tasks(assignee_user_id, task_status, due_on)
- workflow_history(workflow_instance_id, occurred_on)
- audit_events(object_type, object_id, event_time)
- audit_events(correlation_id), audit_events(trace_id)

Partition (goi y giai doan sau):

- audit_events partition theo thang (event_time) neu dung luong tang cao.

## 6) Quy tac dong bo va nhat quan

Trong mot transaction ghi:

1. Update document metadata hoac workflow runtime.
2. Ghi workflow_history (neu co transition).
3. Ghi audit_events.
4. Ghi outbox_events.

Background worker:

- Doc outbox_events PENDING -> publish -> update PUBLISHED.
- Retry theo backoff neu that bai.

## 7) Tuong thich voi hien trang trong repo

- Van giu bang documents de khong vo API hien tai.
- Bo sung workflow_instance_id + status_source de chuyen doi dan.
- Co the tiep tuc cho phep status_source = MANUAL trong giai doan dual-run.

## 8) Migrations da them

Flyway migration da tao:

- td-web/src/main/resources/db/migration/V1.0.4__Create_Workflow_And_Audit_Tables.sql

File nay tao day du table/index/comment cho:

- workflow_definitions, workflow_definition_steps, workflow_definition_transitions
- workflow_instances, workflow_tasks, workflow_history
- audit_events
- outbox_events
- alter table documents de lien ket workflow runtime
