# Document File Management API

## Tổng quan

API quản lý tệp tin tài liệu cung cấp các endpoint cho phép:
- Tải lên tệp tin cho tài liệu
- Danh sách các tệp tin của tài liệu
- Xem chi tiết tệp tin
- Tải xuống tệp tin
- Xóa tệp tin
- Cập nhật tài liệu cùng với tệp tin (Form Data)

## Endpoints

### 1. Tải lên tệp tin

**Endpoint**: `POST /api/v1/documents/{documentId}/files`

**Content Type**: `multipart/form-data`

**Parameters**:
- `documentId` (path): UUID của tài liệu
- `file` (form): Tệp tin cần tải lên (bắt buộc)
- `isPrimary` (form, optional): Đánh dấu là tệp chính (true|false, mặc định: false)
- `description` (form, optional): Mô tả tệp tin

**Required Roles**: USER, ADMIN, PRODUCT_MANAGER, BRAND_MANAGER, DOC_EDITOR

**Response**:
```json
{
  "success": true,
  "data": "550e8400-e29b-41d4-a716-446655440000",
  "message": "File uploaded successfully"
}
```

**cURL Example**:
```bash
curl -X POST "http://localhost:8080/api/v1/documents/550e8400-e29b-41d4-a716-446655440000/files" \
  -H "Authorization: Bearer <TOKEN>" \
  -F "file=@document.pdf" \
  -F "isPrimary=true" \
  -F "description=Main document"
```

---

### 2. Danh sách tệp tin của tài liệu

**Endpoint**: `GET /api/v1/documents/{documentId}/files`

**Parameters**:
- `documentId` (path): UUID của tài liệu
- `pageNumber` (query, optional): Số trang (mặc định: 0)
- `pageSize` (query, optional): Số tệp trên mỗi trang (mặc định: 20)

**Required Roles**: USER, ADMIN, PRODUCT_MANAGER, BRAND_MANAGER

**Response**:
```json
{
  "success": true,
  "data": {
    "content": [
      {
        "file_id": "550e8400-e29b-41d4-a716-446655440000",
        "document_id": "660e8400-e29b-41d4-a716-446655440000",
        "file_name": "document_1.pdf",
        "original_file_name": "document.pdf",
        "file_size": 1024000,
        "mime_type": "application/pdf",
        "upload_date": "2026-03-18T10:30:00",
        "uploaded_by": "user@example.com",
        "storage_path": "/storage/files/550e8400.pdf",
        "is_primary": true,
        "version": 1,
        "description": "Main document",
        "checksum": "abc123def456"
      }
    ],
    "totalElements": 1,
    "totalPages": 1,
    "currentPage": 0,
    "pageSize": 20
  }
}
```

**cURL Example**:
```bash
curl -X GET "http://localhost:8080/api/v1/documents/550e8400-e29b-41d4-a716-446655440000/files?pageNumber=0&pageSize=20" \
  -H "Authorization: Bearer <TOKEN>"
```

---

### 3. Chi tiết tệp tin

**Endpoint**: `GET /api/v1/documents/{documentId}/files/{fileId}`

**Parameters**:
- `documentId` (path): UUID của tài liệu
- `fileId` (path): UUID của tệp tin

**Required Roles**: USER, ADMIN, PRODUCT_MANAGER, BRAND_MANAGER

**Response**:
```json
{
  "success": true,
  "data": {
    "file_id": "550e8400-e29b-41d4-a716-446655440000",
    "document_id": "660e8400-e29b-41d4-a716-446655440000",
    "file_name": "document_1.pdf",
    "original_file_name": "document.pdf",
    "file_size": 1024000,
    "mime_type": "application/pdf",
    "upload_date": "2026-03-18T10:30:00",
    "uploaded_by": "user@example.com",
    "storage_path": "/storage/files/550e8400.pdf",
    "is_primary": true,
    "version": 1,
    "description": "Main document",
    "checksum": "abc123def456"
  }
}
```

**cURL Example**:
```bash
curl -X GET "http://localhost:8080/api/v1/documents/550e8400-e29b-41d4-a716-446655440000/files/770e8400-e29b-41d4-a716-446655440000" \
  -H "Authorization: Bearer <TOKEN>"
```

---

### 4. Xóa tệp tin

**Endpoint**: `DELETE /api/v1/documents/{documentId}/files/{fileId}`

**Parameters**:
- `documentId` (path): UUID của tài liệu
- `fileId` (path): UUID của tệp tin
- `reason` (query, optional): Lý do xóa

**Required Roles**: USER, ADMIN, PRODUCT_MANAGER, BRAND_MANAGER, DOC_EDITOR

**Response**:
```json
{
  "success": true,
  "data": "770e8400-e29b-41d4-a716-446655440000",
  "message": "File deleted successfully"
}
```

**cURL Example**:
```bash
curl -X DELETE "http://localhost:8080/api/v1/documents/550e8400-e29b-41d4-a716-446655440000/files/770e8400-e29b-41d4-a716-446655440000?reason=Invalid%20file" \
  -H "Authorization: Bearer <TOKEN>"
```

---

### 5. Cập nhật tài liệu với tệp tin (Form Data)

**Endpoint**: `PUT /api/v1/documents/{documentId}/with-file`

**Content Type**: `multipart/form-data`

**Parameters**:
- `documentId` (path): UUID của tài liệu
- `title` (form, optional): Tiêu đề mới
- `documentType` (form, optional): Loại tài liệu
- `status` (form, optional): Trạng thái
- `content` (form, optional): Nội dung
- `file` (form, optional): Tệp tin mới
- `isPrimaryFile` (form, optional): Đánh dấu tệp là chính (true|false, mặc định: false)
- `fileDescription` (form, optional): Mô tả tệp tin

**Required Roles**: USER, ADMIN, PRODUCT_MANAGER, BRAND_MANAGER, DOC_EDITOR

**Response**:
```json
{
  "success": true,
  "data": "550e8400-e29b-41d4-a716-446655440000",
  "message": "Document updated with file successfully"
}
```

**cURL Example**:
```bash
curl -X PUT "http://localhost:8080/api/v1/documents/550e8400-e29b-41d4-a716-446655440000/with-file" \
  -H "Authorization: Bearer <TOKEN>" \
  -F "title=Updated Document Title" \
  -F "status=Active" \
  -F "content=New content here" \
  -F "file=@new_document.pdf" \
  -F "isPrimaryFile=true" \
  -F "fileDescription=Updated main document"
```

---

### 6. Tải xuống tệp tin

**Endpoint**: `GET /api/v1/documents/{documentId}/files/{fileId}/download`

**Parameters**:
- `documentId` (path): UUID của tài liệu
- `fileId` (path): UUID của tệp tin

**Required Roles**: USER, ADMIN, PRODUCT_MANAGER, BRAND_MANAGER

**Response**: Tệp tin được tải xuống với các header:
- `Content-Disposition`: attachment; filename="..."
- `Content-Type`: Loại MIME tương ứng
- `Content-Length`: Kích thước tệp

**cURL Example**:
```bash
curl -X GET "http://localhost:8080/api/v1/documents/550e8400-e29b-41d4-a716-446655440000/files/770e8400-e29b-41d4-a716-446655440000/download" \
  -H "Authorization: Bearer <TOKEN>" \
  -o downloaded_file.pdf
```

---

## HTTP Status Codes

| Status Code | Meaning |
|-------------|---------|
| 200 OK | Request thành công |
| 201 Created | Tệp tin được tạo thành công |
| 400 Bad Request | Dữ liệu không hợp lệ |
| 401 Unauthorized | Thiếu hoặc không có authorizatio |
| 403 Forbidden | Không có quyền truy cập |
| 404 Not Found | Tài liệu / Tệp tin không tìm thấy |
| 500 Internal Server Error | Lỗi máy chủ |

---

## Swagger UI

Tất cả các endpoint trên đều được tự động tài liệu hoá trong Swagger UI:

**URL**: `http://localhost:8080/swagger-ui.html`

### Tính năng Swagger:
- ✅ Form data support cho file upload
- ✅ Multipart form data visualization
- ✅ Parameter documentation
- ✅ Try it out functionality
- ✅ Response schema examples
- ✅ Authorization header support

---

## Errors

### Lỗi chung

```json
{
  "success": false,
  "message": "Error message",
  "errorCode": "ERROR_CODE"
}
```

### Ví dụ các lỗi thường gặp

**File quá lớn**:
```json
{
  "success": false,
  "message": "File size exceeds maximum allowed size of 50MB",
  "errorCode": "FILE_SIZE_EXCEEDED"
}
```

**Loại tệp không hỗ trợ**:
```json
{
  "success": false,
  "message": "File type 'executable' is not allowed. Allowed types: pdf, doc, docx, xls, xlsx, txt",
  "errorCode": "INVALID_FILE_TYPE"
}
```

**Tài liệu không tìm thấy**:
```json
{
  "success": false,
  "message": "Document not found with ID: 550e8400-e29b-41d4-a716-446655440000",
  "errorCode": "DOCUMENT_NOT_FOUND"
}
```

---

## Validation Rules

### File Upload

- **Kích thước tệp**: 0 - 50 MB
- **Loại tệp được phép**: PDF, DOC, DOCX, XLS, XLSX, TXT, ZIP, RAR
- **Tên tệp**: 1-255 ký tự, không chứa ký tự đặc biệt
- **Checksum**: Tự động tính toán SHA-256

### Document Update

- **Tiêu đề**: 2-300 ký tự
- **Loại tài liệu**: Tối đa 100 ký tự
- **Trạng thái**: Tối đa 50 ký tự
- **Nội dung**: Không bị giới hạn kích thước

---

## Security

- Tất cả các endpoint yêu cầu **JWT Bearer token**
- Quyền truy cập được kiểm soát bằng roles
- Các tệp tin được lưu trữ ở vị trí có quyền truy cập hạn chế
- Checksum được xác minh khi tải xuống
- Audit log được ghi cho tất cả các thao tác tệp tin

---

## Implementation Status

### ✅ Completed
- API endpoint definitions (6 endpoints)
- Request/Response DTOs
- Swagger/OpenAPI documentation
- Security role checks (@PreAuthorize)
- Form data support for multipart uploads

### ⏳ Pending
- File storage implementation (MinIO integration)
- File persistence layer (Repository)
- Checksum calculation
- File download streaming
- File size validation
- File type validation
- Audit event recording
