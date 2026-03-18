# API contract de tach Document, Workflow, Audit Log

Tai lieu nay de xac dinh API contract cho 3 service/3 module nghiep vu.

## 1) Nguyen tac API

- Dung token Keycloak (Bearer JWT) va role-based access.
- Moi request nen gui them X-Request-Id va X-Correlation-Id.
- Response loi su dung format thong nhat: code, message, details.
- API mutating phai idempotent neu co kha nang retry (submit, approve, reject).

## 2) Document API

Base path: /api/v1/documents

## 2.1 Tao va cap nhat

1. POST /
- Muc dich: tao document o trang thai ban dau.
- Role: USER, ADMIN, DOC_EDITOR
- Body:

{
  "title": "Trinh duyet mua sam thiet bi",
  "documentType": "PROCUREMENT_REQUEST",
  "content": "Noi dung de nghi...",
  "tags": ["mua-sam", "nam-2026"],
  "attributes": {
    "department": "IT",
    "amount": 120000000
  },
  "metadata": {
    "priority": "HIGH"
  }
}

- Success: 201 + documentId

2. PUT /{id}
- Muc dich: cap nhat noi dung/thuoc tinh document.
- Rule: neu status_source = WORKFLOW thi khong cho cap nhat status truc tiep.
- Role: USER, ADMIN, DOC_EDITOR
- Success: 200

## 2.2 Tra cuu

3. GET /
- Muc dich: danh sach co phan trang.
- Query: pageNumber, pageSize, sortBy, sortDirection, keyword, documentType, status
- Role: USER, ADMIN, DOC_VIEWER

4. GET /{id}
- Muc dich: chi tiet document.

5. POST /search
- Muc dich: tim kiem nang cao theo bo loc.

## 2.3 Vong doi document (goi qua workflow)

6. POST /{id}/submit
- Muc dich: nop document vao workflow.
- Action: tao workflow instance + update status = SUBMITTED.

7. POST /{id}/withdraw
- Muc dich: rut document truoc khi duoc duyet xong (neu policy cho phep).

8. GET /{id}/timeline
- Muc dich: tong hop workflow history + audit events de hien thi lich su.

## 2.4 Xoa va khoi phuc

9. DELETE /{id}
- Soft delete.

10. DELETE /{id}/permanent
- Hard delete (ADMIN).

11. POST /deleted/search
- Tim danh sach da soft-delete.

## 3) Workflow API

Base path: /api/v1/workflows

## 3.1 Workflow definition

1. POST /definitions
- Tao workflow definition moi.
- Role: ADMIN, WORKFLOW_ADMIN

2. PUT /definitions/{id}
- Sua definition draft.

3. POST /definitions/{id}/publish
- Khoa version va phat hanh.

4. GET /definitions
- Danh sach definition + version.

5. GET /definitions/{id}
- Chi tiet definition (steps + transitions).

## 3.2 Workflow runtime

6. POST /instances
- Tao workflow instance cho entity.
- Body:

{
  "definitionCode": "DOC_APPROVAL",
  "definitionVersion": 3,
  "entityType": "DOCUMENT",
  "entityId": "uuid",
  "context": {
    "department": "IT"
  }
}

7. GET /instances/{id}
- Chi tiet runtime: step hien tai, status, assignee.

8. POST /instances/{id}/actions/{actionCode}
- Thuc hien transition (approve/reject/return/escalate...).
- Headers: Idempotency-Key bat buoc.
- Body:

{
  "comment": "Dong y de xuat",
  "payload": {
    "approvedAmount": 100000000
  }
}

9. POST /instances/{id}/cancel
- Huy workflow instance.

10. GET /instances/{id}/history
- Lich su transition chi tiet.

## 3.3 Task inbox

11. GET /tasks/my
- Danh sach task cua user dang dang nhap.
- Query: status, page, size

12. GET /tasks/group/{groupCode}
- Danh sach task theo nhom role/department.

13. POST /tasks/{taskId}/claim
- Nhan task.

14. POST /tasks/{taskId}/delegate
- Chuyen task cho user khac.

## 4) Audit Log API

Base path: /api/v1/audit-events

1. POST /search
- Tim kiem audit event theo bo loc.
- Query body goi y:

{
  "fromTime": "2026-03-01T00:00:00Z",
  "toTime": "2026-03-31T23:59:59Z",
  "objectType": "DOCUMENT",
  "objectId": "uuid",
  "actorId": "uuid",
  "action": "DOCUMENT_APPROVED",
  "result": "SUCCESS",
  "correlationId": "corr-123"
}

2. GET /{id}
- Chi tiet 1 su kien audit.

3. GET /objects/{objectType}/{objectId}
- Timeline audit theo doi tuong.

4. GET /requests/{requestId}
- Truy vet theo request.

5. GET /correlations/{correlationId}
- Truy vet su kien lien service theo correlation.

## 5) Event contract (async)

Phat event sau moi transition hoac document update quan trong:

- DocumentCreated
- DocumentUpdated
- DocumentSubmitted
- WorkflowTaskAssigned
- WorkflowTransitioned
- DocumentApproved
- DocumentRejected
- AuditEventRecorded

Payload toi thieu:

{
  "eventId": "uuid",
  "eventType": "WorkflowTransitioned",
  "occurredOn": "2026-03-18T09:00:00Z",
  "entityType": "DOCUMENT",
  "entityId": "uuid",
  "actorId": "uuid",
  "requestId": "req-001",
  "correlationId": "corr-001",
  "data": {}
}

## 6) Ma loi de chuan hoa

- DOC-404: Document not found
- DOC-409: Document state conflict
- WF-400: Invalid workflow action
- WF-403: Action forbidden by role/policy
- WF-409: Optimistic lock/version conflict
- AUD-400: Invalid audit filter

## 7) Mapping voi API hien tai trong repo

Dang co san:

- GET /api/v1/documents
- GET /api/v1/documents/{id}
- POST /api/v1/documents/search
- POST /api/v1/documents
- PUT /api/v1/documents/{id}
- DELETE /api/v1/documents/{id}
- DELETE /api/v1/documents/{id}/permanent
- POST /api/v1/documents/deleted/search

Can bo sung them:

- Document lifecycle endpoints (submit, withdraw, timeline)
- Workflow definition/runtime/task endpoints
- Audit events search/detail endpoints theo schema moi
