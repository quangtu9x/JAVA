# File Management API - Swagger UI Integration

## Overview

Tất cả các endpoint quản lý tệp tin đã được tích hợp vào Swagger UI với đầy đủ tài liệu, examples, và hỗ trợ **Try it out** functionality.

## API Endpoints Summary

### Endpoint Tree

```
/api/v1/documents
├── /{documentId}
│   └── /files
│       ├── [POST]   ↓ Upload file
│       ├── [GET]    ↓ List files
│       └── /{fileId}
│           ├── [GET]     ↓ Get file info
│           ├── [DELETE]  ↓ Delete file
│           └── /download [GET]  ↓ Download file
└── /{documentId}
    └── /with-file [PUT] ↓ Update document with file (Form Data)
```

## File API Endpoints (New)

| # | Method | Endpoint | Function | Form Data |
|---|--------|----------|----------|-----------|
| 1 | POST | `/api/v1/documents/{documentId}/files` | Tải lên tệp tin | ✅ Yes |
| 2 | GET | `/api/v1/documents/{documentId}/files` | Danh sách tệp tin | ❌ No |
| 3 | GET | `/api/v1/documents/{documentId}/files/{fileId}` | Chi tiết tệp tin | ❌ No |
| 4 | DELETE | `/api/v1/documents/{documentId}/files/{fileId}` | Xóa tệp tin | ❌ No |
| 5 | PUT | `/api/v1/documents/{documentId}/with-file` | Cập nhật + upload file | ✅ Yes |
| 6 | GET | `/api/v1/documents/{documentId}/files/{fileId}/download` | Tải xuống | ❌ No |

## Swagger UI Features

### Form Data Support

Swagger UI hiển thị file upload form với:

```
POST /api/v1/documents/{documentId}/files

Parameters:
├── documentId (path): UUID ✓ Required
├── file (form): MultipartFile ✓ Required
├── isPrimary (form): Boolean (false)
└── description (form): String (optional)

Try it out:
┌─────────────────────────────────────────┐
│ documentId: 550e8400-...                │
│ file: [Choose File]  [document.pdf]    │
│ isPrimary: [✓] true  [  ] false        │
│ description: Main document              │
│                                          │
│ [Execute]  [Cancel]                    │
└─────────────────────────────────────────┘
```

### Response Schemas

Mỗi endpoint đều hiển thị response schema chi tiết:

**FileDto Schema**:
```json
{
  "file_id": "UUID",
  "document_id": "UUID",
  "file_name": "string",
  "original_file_name": "string",
  "file_size": "integer",
  "mime_type": "string",
  "upload_date": "date-time",
  "uploaded_by": "string",
  "storage_path": "string",
  "is_primary": "boolean",
  "version": "integer",
  "description": "string",
  "checksum": "string"
}
```

## Security Configuration

Tất cả endpoints được bảo vệ bằng JWT authentication:

```java
@PreAuthorize("hasAnyRole('USER', 'ADMIN', ...)")
```

Swagger UI hiển thị:
- 🔒 Lock icon cho các endpoint được bảo vệ
- Role requirements trong mô tả
- Authorize button để set JWT token

## OpenAPI 3.0 Specification

### Swagger Annotations Used

```java
// Endpoint metadata
@Operation(summary = "...", description = "...")

// Parameter documentation  
@Parameter(description = "...", required = true/false)

// Form data support
@RequestParam(value = "file") MultipartFile file

// Content type declaration
consumes = MediaType.MULTIPART_FORM_DATA_VALUE

// Media type for swagger
@Content(mediaType = MediaType.APPLICATION_OCTET_STREAM_VALUE)

// Response documentation
@PreAuthorize("hasAnyRole(...)")
```

## Accessing Swagger UI

### URL
```
http://localhost:8080/swagger-ui.html
```

### Tab Organization

Endpoints được tổ chức trong tag **Documents**:

```
Documents  ⊟
├─ POST   /api/v1/documents
├─ GET    /api/v1/documents
├─ POST   /api/v1/documents/search
├─ GET    /api/v1/documents/{id}
├─ PUT    /api/v1/documents/{id}
├─ DELETE /api/v1/documents/{id}
├─ DELETE /api/v1/documents/{id}/permanent
├─ POST   /api/v1/documents/deleted/search
│
├─ ✨ NEW: POST   /api/v1/documents/{documentId}/files
├─ ✨ NEW: GET    /api/v1/documents/{documentId}/files
├─ ✨ NEW: GET    /api/v1/documents/{documentId}/files/{fileId}
├─ ✨ NEW: DELETE /api/v1/documents/{documentId}/files/{fileId}
├─ ✨ NEW: GET    /api/v1/documents/{documentId}/files/{fileId}/download
└─ ✨ NEW: PUT    /api/v1/documents/{documentId}/with-file
```

## Example Workflows in Swagger UI

### Workflow 1: Upload File

1. Navigate to **POST** `/api/v1/documents/{documentId}/files`
2. Click **Try it out**
3. Fill in:
   - `documentId`: 550e8400-e29b-41d4-a716-446655440000
   - `file`: Choose your PDF/DOC file
   - `isPrimary`: true
   - `description`: Main document
4. Click **Execute**
5. See response with file_id

### Workflow 2: List Files

1. Navigate to **GET** `/api/v1/documents/{documentId}/files`
2. Click **Try it out**
3. Fill in:
   - `documentId`: 550e8400-e29b-41d4-a716-446655440000
   - `pageNumber`: 0
   - `pageSize`: 20
4. Click **Execute**
5. View list of files with pagination

### Workflow 3: Update Document with File

1. Navigate to **PUT** `/api/v1/documents/{documentId}/with-file`
2. Click **Try it out**
3. Fill in form fields:
   - `documentId`: 550e8400-e29b-41d4-a716-446655440000
   - `title`: New Title
   - `status`: Active
   - `file`: Choose new file
   - `isPrimaryFile`: true
4. Click **Execute**
5. Document and file updated successfully

## Swagger Configuration in Spring

File application.yml (Auto-configured by Springdoc OpenAPI):

```yaml
springdoc:
  swagger-ui:
    path: /swagger-ui.html
    enabled: true
    operations-sorter: method
    tags-sorter: alpha
    display-request-duration: true
    doc-expansion: list
    try-it-out-enabled: true
  api-docs:
    path: /v3/api-docs
    enabled: true
  show-actuator: false
```

## File Classes Structure

### Request Classes

```
td-application/src/main/java/com/td/application/documents/
├── UploadFileRequest
├── UpdateDocumentWithFileRequest
├── DownloadFileRequest
└── DeleteFileRequest
```

### DTO Classes

```
td-application/src/main/java/com/td/application/documents/
└── FileDto
```

### Use Case Interfaces

```
td-application/src/main/java/com/td/application/documents/
├── UploadFileUseCase
├── GetFileUseCase
├── ListDocumentFilesUseCase
├── DeleteFileUseCase
└── UpdateDocumentWithFileUseCase
```

### Controller

```
td-web/src/main/java/com/td/web/controllers/v1/
└── DocumentsController (6 new endpoints)
```

## API Documentation File

Complete API documentation with examples:
```
docs/DOCUMENT_FILE_MANAGEMENT_API.md
```

This file includes:
- ✅ Complete endpoint descriptions
- ✅ Request/Response examples
- ✅ cURL examples for testing
- ✅ HTTP status codes
- ✅ Error handling
- ✅ Validation rules
- ✅ Security requirements

## Testing the API

### Using Swagger UI

1. Open http://localhost:8080/swagger-ui.html
2. Scroll to **Documents** section
3. Find **File Management** endpoints (marked with ✨ NEW)
4. Click **Try it out** on any endpoint
5. Fill in parameters
6. Click **Execute**
7. View response

### Using cURL

```bash
# Upload file
curl -X POST "http://localhost:8080/api/v1/documents/{documentId}/files" \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@document.pdf" \
  -F "isPrimary=true"

# List files
curl -X GET "http://localhost:8080/api/v1/documents/{documentId}/files" \
  -H "Authorization: Bearer $TOKEN"

# Update with file
curl -X PUT "http://localhost:8080/api/v1/documents/{documentId}/with-file" \
  -H "Authorization: Bearer $TOKEN" \
  -F "title=New Title" \
  -F "file=@new_doc.pdf"
```

### Using Postman

Import OpenAPI schema at: `http://localhost:8080/v3/api-docs`

Or manually create requests:
- Set method: POST/GET/PUT/DELETE
- Set URL: http://localhost:8080/api/v1/documents/...
- Set Headers: Authorization: Bearer <TOKEN>
- Set Body: form-data (for file upload)

## Implementation Checklist

### ✅ Completed
- [x] 6 API endpoints defined
- [x] 5 Request/Response DTOs created
- [x] 5 Use case interfaces defined
- [x] Full Swagger/OpenAPI annotations
- [x] JWT authentication configured
- [x] Role-based access control (@PreAuthorize)
- [x] Form data support for file upload
- [x] Multipart upload configuration
- [x] API documentation with examples
- [x] Validation without errors

### ⏳ Pending Implementation
- [ ] File storage backend (MinIO/S3/Local)
- [ ] Database persistence (JPA entities)
- [ ] File checksum calculation
- [ ] File size/type validation
- [ ] Audit event recording
- [ ] Integration tests
- [ ] Performance testing

## Next Steps

1. **Implement Use Cases**: Create implementations for all 5 use case interfaces
2. **Add Persistence**: Create JPA repositories and entities
3. **File Storage**: Integrate with MinIO or cloud storage
4. **Testing**: Write unit and integration tests
5. **Documentation**: Update API docs with actual implementation details
6. **Deployment**: Build and test in staging environment
