# Hướng dẫn tách và nhân phân hệ độc lập

Index tài liệu: [INDEX_TAI_LIEU_API.md](INDEX_TAI_LIEU_API.md)

Checklist tổng hợp: [CHECKLIST_NHAN_BAN_VA_SUA_API.md](CHECKLIST_NHAN_BAN_VA_SUA_API.md)

Hướng dẫn nhân bản feature đơn lẻ: [HUONG_DAN_NHAN_BAN_VA_SUA_API.md](HUONG_DAN_NHAN_BAN_VA_SUA_API.md)

## 1. Mục tiêu

Tài liệu này dùng khi anh muốn phát triển hệ thống thành nhiều phân hệ độc lập với nhau nhưng vẫn chạy chung trên cùng codebase 4 layer hiện tại.

Các phân hệ anh đang cần:

- danh mục
- văn bản đến
- văn bản đi
- dự thảo
- hồ sơ công việc
- lịch công tác
- theo dõi nhiệm vụ

Mục tiêu đúng không phải là copy controller và bảng thật nhanh, mà là tách ranh giới nghiệp vụ ngay từ đầu để sau này không bị chồng chéo dữ liệu, query chéo bừa bãi, hay quyền truy cập khó kiểm soát.

## 2. Nguyên tắc tách phân hệ

Mỗi phân hệ phải có:

- package riêng trong td-domain
- package riêng trong td-application
- repository implementation riêng trong td-infrastructure
- controller riêng trong td-web
- bảng dữ liệu riêng hoặc nhóm bảng riêng
- permission riêng
- cache riêng nếu cần

Không nên làm:

- một entity chung ôm quá nhiều nghiệp vụ của nhiều phân hệ
- query join trực tiếp entity của phân hệ khác nếu chỉ cần tham chiếu ID
- để controller của phân hệ này gọi thẳng repository của phân hệ khác
- dùng một bảng categories chung cho mọi thứ, kể cả user, role, org tree, workflow state

Nguyên tắc phụ thuộc nên là:

- phân hệ chỉ tham chiếu sang phân hệ khác bằng ID hoặc DTO đọc-only
- nếu cần gọi logic của phân hệ khác, đi qua facade hoặc use case public
- danh mục dùng chung là read-mostly
- user, role, cơ cấu tổ chức là shared core, không phải categories thường

## 3. Chia lớp ngay từ đầu

Trước khi tạo từng phân hệ, nên chia hệ thống thành 3 nhóm lớn:

### A. Shared core dùng chung

Nhóm này không phải phân hệ nghiệp vụ đầu cuối, mà là nền tảng dùng chung cho các phân hệ khác:

- cơ cấu tổ chức
- người dùng
- vai trò
- quyền
- data scope
- context người dùng hiện tại

Nhóm này nên ổn định trước khi mở rộng các phân hệ văn bản.

### B. Shared master data

Nhóm danh mục dùng chung:

- loại văn bản
- cơ quan ngoài
- độ mật
- độ khẩn
- lĩnh vực
- chức vụ
- trạng thái dùng chung nếu có

Nhóm này có thể dùng pattern CRUD + cache gần giống categories.

### C. Phân hệ nghiệp vụ độc lập

- văn bản đến
- văn bản đi
- dự thảo
- hồ sơ công việc
- lịch công tác
- theo dõi nhiệm vụ

## 4. Ranh giới đề xuất cho từng phân hệ

### 4.1. Danh mục

Phân hệ này chỉ sở hữu các bảng master data dùng chung.

Nên chứa:

- document types
- external organizations
- confidentiality levels
- urgency levels
- domains or fields
- positions nếu mới chỉ là danh mục chọn

Không nên chứa:

- user
- role
- permission
- organization tree

### 4.2. Văn bản đến

Phân hệ này sở hữu toàn bộ nghiệp vụ tiếp nhận văn bản từ bên ngoài hoặc từ đơn vị khác gửi đến.

Nên chứa:

- incoming document aggregate
- thông tin số đến, ngày đến, nơi gửi, nơi nhận
- metadata nghiệp vụ riêng của văn bản đến
- liên kết file scan, bản gốc, phụ lục
- workflow xử lý văn bản đến nếu có

Chỉ tham chiếu sang phân hệ khác bằng ID:

- document type id
- external organization id
- confidentiality level id
- urgency level id
- assigned org id
- assigned user id

### 4.3. Văn bản đi

Phân hệ này sở hữu nghiệp vụ ban hành, phát hành, gửi ra ngoài.

Nên chứa:

- outgoing document aggregate
- số đi, ngày ban hành, người ký, nơi nhận
- recipient list
- trạng thái phát hành
- file đính kèm và bản ký

Không dùng chung bảng chính với văn bản đến.

### 4.4. Dự thảo

Phân hệ này nên độc lập với văn bản đi, nhưng có thể chuyển hóa sang văn bản đi ở bước phát hành.

Nên chứa:

- draft aggregate
- version của dự thảo
- trạng thái soạn thảo, trình duyệt, trả lại, chờ ký
- lịch sử chỉnh sửa nếu cần
- liên kết tới văn bản đi khi phát hành thành công

Không nên nhét dự thảo chung vào bảng văn bản đi rồi dùng trạng thái để phân biệt tất cả mọi case.

### 4.5. Hồ sơ công việc

Phân hệ này dùng để gom nhóm tài liệu, nhiệm vụ, trao đổi quanh một đầu việc.

Nên chứa:

- work file aggregate
- mã hồ sơ, tên hồ sơ, thời hạn, chủ trì
- danh sách liên kết document id
- danh sách liên kết task id
- đơn vị chủ trì, người phụ trách

Hồ sơ công việc không nên sở hữu bản thân dữ liệu của văn bản đến, văn bản đi, hay nhiệm vụ. Nó chỉ quản lý mối liên kết.

### 4.6. Lịch công tác

Phân hệ này sở hữu sự kiện công tác, lịch họp, lịch làm việc.

Nên chứa:

- schedule aggregate
- thời gian bắt đầu, kết thúc, địa điểm, nội dung
- thành phần tham gia
- đơn vị chủ trì
- liên kết hồ sơ hoặc nhiệm vụ nếu có

### 4.7. Theo dõi nhiệm vụ

Phân hệ này sở hữu task và tiến độ xử lý.

Nên chứa:

- task aggregate
- người giao, người nhận, đơn vị thực hiện
- hạn xử lý
- trạng thái
- mức ưu tiên
- liên kết hồ sơ công việc, lịch công tác, document nếu cần

Task không nên nhét vào workflow state của văn bản rồi dùng chung mọi nơi.

## 5. Thứ tự triển khai nên làm

Không nên triển khai đồng thời cả 7 phân hệ ngay từ đầu.

Thứ tự nên là:

1. shared core: organization, user, role, permission, data scope
2. danh mục dùng chung
3. dự thảo
4. văn bản đến
5. văn bản đi
6. hồ sơ công việc
7. theo dõi nhiệm vụ
8. lịch công tác

Lý do:

- các phân hệ văn bản cần user context và data scope ổn định trước
- danh mục dùng chung cần sẵn trước để các phân hệ khác chỉ tham chiếu ID
- dự thảo thường là điểm vào nhẹ hơn văn bản đi
- hồ sơ, nhiệm vụ, lịch công tác thường là lớp orchestration, nên làm sau khi document core đã rõ

## 6. Quy trình từng bước để nhân một phân hệ mới

### Bước 1. Chốt ranh giới nghiệp vụ của phân hệ

Trước khi code, trả lời rõ:

- phân hệ sở hữu bảng nào
- phân hệ không sở hữu bảng nào mà chỉ tham chiếu ID
- action chính là gì
- lifecycle chính là gì
- permission chính là gì
- dữ liệu cần tìm kiếm là gì
- dữ liệu nào phải nằm trong Elasticsearch, dữ liệu nào chỉ cần PostgreSQL

Ví dụ:

- văn bản đến sở hữu incoming_documents
- văn bản đến không sở hữu external_organizations, chỉ giữ externalOrganizationId
- hồ sơ công việc không sở hữu document, chỉ giữ linkedDocumentIds hoặc bảng liên kết

### Bước 2. Đặt tên chuẩn cho package, route, bảng

Đặt tên ngay từ đầu theo [QUY_UOC_DAT_TEN_FEATURE_API.md](QUY_UOC_DAT_TEN_FEATURE_API.md).

Ví dụ package đề xuất:

- com.td.domain.masterdata
- com.td.domain.incomingdocuments
- com.td.domain.outgoingdocuments
- com.td.domain.drafts
- com.td.domain.workfiles
- com.td.domain.schedules
- com.td.domain.tasks

Route gợi ý:

- /api/v1/master-data/document-types
- /api/v1/incoming-documents
- /api/v1/outgoing-documents
- /api/v1/drafts
- /api/v1/work-files
- /api/v1/schedules
- /api/v1/tasks

### Bước 3. Tạo migration trước

Với mỗi phân hệ, tạo bảng trước rồi mới tạo entity.

Checklist:

- có bảng chính
- có bảng liên kết nếu cần many-to-many
- có deleted_on nếu dùng soft delete
- có index cho field search/filter/sort
- có foreign key sang bảng shared nếu thật sự cần
- nếu chỉ cần loose coupling, cân nhắc chỉ lưu UUID/string ID, không bắt buộc FK cứng ngay

Ví dụ:

- incoming_documents
- outgoing_documents
- drafts
- work_files
- work_file_documents
- schedules
- tasks

### Bước 4. Tạo Domain entity riêng cho phân hệ

Mỗi phân hệ phải có entity riêng trong td-domain.

Ví dụ:

- IncomingDocument
- OutgoingDocument
- DraftDocument
- WorkFile
- ScheduleEvent
- TaskItem

Không tạo một entity BusinessDocument chung cho tất cả case nếu lifecycle khác nhau rõ rệt.

Nếu muốn dùng shared model cho phần metadata chung, tách thành value object hoặc component dùng chung, không ép mọi phân hệ dùng một bảng chính.

### Bước 5. Tạo Application layer riêng

Cho từng phân hệ, tạo package use case riêng trong td-application.

Mỗi phân hệ thường cần:

- DTO
- request create/update/search
- repository interface
- use case create/get/update/delete/search
- facade public nếu phân hệ khác cần đọc dữ liệu của nó

Ví dụ với văn bản đến:

- CreateIncomingDocumentRequest
- UpdateIncomingDocumentRequest
- SearchIncomingDocumentsRequest
- IncomingDocumentDto
- IncomingDocumentRepository
- CreateIncomingDocumentUseCase
- SearchIncomingDocumentsUseCase

### Bước 6. Tạo Infrastructure layer riêng

Trong td-infrastructure, tạo repository implementation và query logic riêng cho phân hệ.

Ví dụ:

- IncomingDocumentJpaRepository
- PostgresIncomingDocumentRepository
- OutgoingDocumentJpaRepository
- PostgresOutgoingDocumentRepository

Không để một repository chung xử lý mọi phân hệ document nếu điều đó làm query ngày càng đầy if else.

### Bước 7. Tạo Web controller riêng

Trong td-web, mỗi phân hệ có controller riêng.

Ví dụ:

- IncomingDocumentsController
- OutgoingDocumentsController
- DraftsController
- WorkFilesController
- SchedulesController
- TasksController

Controller chỉ orchestration:

- nhận request
- gọi use case
- trả response
- gắn auth và validation

### Bước 8. Tách permission theo phân hệ

Mỗi phân hệ phải có role hoặc permission riêng.

Ví dụ:

- INCOMING_DOC_READ
- INCOMING_DOC_CREATE
- INCOMING_DOC_ASSIGN
- OUTGOING_DOC_ISSUE
- DRAFT_EDIT
- WORK_FILE_MANAGE
- TASK_ASSIGN
- SCHEDULE_MANAGE

Không chỉ dùng USER và ADMIN cho toàn bộ hệ thống nếu anh muốn các phân hệ thực sự độc lập.

### Bước 9. Tách data scope theo phân hệ

Permission endpoint chưa đủ. Với văn bản, hồ sơ, nhiệm vụ, lịch công tác, phải có data scope.

Ví dụ:

- chỉ xem dữ liệu đơn vị hiện tại
- xem đơn vị hiện tại và đơn vị con
- chỉ xem dữ liệu do bản thân tạo
- xem theo vai trò được phân công xử lý

Khi query search/list/detail, phải lấy CurrentUserContext rồi gắn thêm filter theo scope.

Không để FE truyền org filter rồi backend tin thẳng.

### Bước 10. Chỉ tham chiếu chéo bằng ID hoặc facade

Ví dụ đúng:

- TaskItem giữ workFileId
- WorkFile giữ linkedDocumentIds hoặc bảng liên kết work_file_documents
- ScheduleEvent giữ relatedTaskId nếu có

Ví dụ không nên làm:

- Task controller query trực tiếp bảng outgoing_documents bằng native SQL riêng
- Work file service sửa thẳng entity draft của phân hệ dự thảo

Nếu cần đọc chéo, tạo facade đọc-only ở application layer.

### Bước 11. Quyết định cache và search cho từng phân hệ

Không phải phân hệ nào cũng cần Redis cache hoặc Elasticsearch ngay.

Gợi ý:

- danh mục: cần cache by-id và list
- văn bản đến/đi: cần search mạnh, có thể dùng Elasticsearch
- dự thảo: có thể chỉ PostgreSQL trước nếu volume chưa lớn
- hồ sơ công việc: cần search vừa phải, PostgreSQL trước
- lịch công tác: PostgreSQL thường đủ ở giai đoạn đầu
- nhiệm vụ: PostgreSQL trước, bổ sung search sau nếu cần

### Bước 12. Verify độc lập trước khi nối chéo

Mỗi phân hệ phải chạy độc lập trước:

- create
- detail
- list/search
- update
- delete hoặc archive
- auth
- data scope

Chỉ sau khi phân hệ tự ổn mới thêm liên kết chéo với phân hệ khác.

## 7. Cấu trúc package gợi ý cho từng phân hệ

Ví dụ cho văn bản đến:

- td-domain/src/main/java/com/td/domain/incomingdocuments/IncomingDocument.java
- td-application/src/main/java/com/td/application/incomingdocuments/IncomingDocumentDto.java
- td-application/src/main/java/com/td/application/incomingdocuments/CreateIncomingDocumentRequest.java
- td-application/src/main/java/com/td/application/incomingdocuments/SearchIncomingDocumentsRequest.java
- td-application/src/main/java/com/td/application/incomingdocuments/IncomingDocumentRepository.java
- td-application/src/main/java/com/td/application/incomingdocuments/CreateIncomingDocumentUseCase.java
- td-infrastructure/src/main/java/com/td/infrastructure/persistence/repository/IncomingDocumentJpaRepository.java
- td-infrastructure/src/main/java/com/td/infrastructure/persistence/repository/PostgresIncomingDocumentRepository.java
- td-web/src/main/java/com/td/web/controllers/v1/IncomingDocumentsController.java

Làm tương tự cho:

- outgoingdocuments
- drafts
- workfiles
- schedules
- tasks

## 8. Cách dùng chung danh mục mà vẫn giữ phân hệ độc lập

Các phân hệ văn bản, hồ sơ, lịch, nhiệm vụ đều chỉ nên tham chiếu tới danh mục dùng chung bằng ID.

Ví dụ:

- incomingDocument.documentTypeId
- incomingDocument.confidentialityLevelId
- outgoingDocument.urgencyLevelId
- taskItem.positionId nếu có

Khi trả response, có 2 cách:

1. chỉ trả ID, FE tự gọi danh mục để render
2. backend enrich thêm tên danh mục qua facade đọc-only

Nếu muốn giữ độc lập tốt, nên ưu tiên cách 1 hoặc enrich ở application facade, không join thẳng entity chéo phân hệ trong domain.

## 9. Cách nối các phân hệ với nhau mà không bị dính chặt

### Dự thảo sang văn bản đi

- draft có thể có action publish
- publish tạo outgoing document mới
- draft giữ outgoingDocumentId sau khi publish
- không đổi draft thành outgoing document ngay trong cùng bảng

### Văn bản đến hoặc đi gắn hồ sơ công việc

- work file quản lý liên kết document
- document không cần biết toàn bộ logic của work file

### Nhiệm vụ gắn hồ sơ hoặc lịch công tác

- task giữ workFileId hoặc scheduleId nếu có
- task không sở hữu dữ liệu lịch hoặc hồ sơ

## 10. Roadmap thực hiện khuyến nghị

### Giai đoạn 1. Shared core

- cơ cấu tổ chức
- người dùng
- role
- permission
- data scope

### Giai đoạn 2. Shared master data

- loại văn bản
- cơ quan ngoài
- độ mật
- độ khẩn
- lĩnh vực
- chức vụ

### Giai đoạn 3. Document core

- dự thảo
- văn bản đến
- văn bản đi

### Giai đoạn 4. Coordination layer

- hồ sơ công việc
- theo dõi nhiệm vụ
- lịch công tác

## 11. Thứ tự thao tác thực tế khi anh bắt đầu ngay hôm nay

Nếu bắt đầu từ codebase hiện tại, nên đi như sau:

1. Chốt shared core trước: organization, user, role, permission, data scope.
2. Dùng pattern categories để dựng phân hệ danh mục dùng chung.
3. Tạo phân hệ drafts trước vì nó ít phụ thuộc external flow hơn.
4. Tạo incoming documents.
5. Tạo outgoing documents.
6. Tạo work files chỉ với link document IDs, chưa cần orchestration quá sâu.
7. Tạo tasks.
8. Tạo schedules.
9. Cuối cùng mới thêm logic nối chéo, dashboard, tổng hợp liên phân hệ.

## 12. Checklist chốt cho mỗi phân hệ trước khi merge

- [ ] Có package riêng ở đủ 4 layer
- [ ] Có bảng riêng và migration riêng
- [ ] Có permission riêng
- [ ] Có data scope rõ ràng
- [ ] Không query trực tiếp dữ liệu phân hệ khác nếu chỉ cần ID
- [ ] Có API create/detail/list/update/delete hoặc lifecycle tương ứng
- [ ] Build từ root thành công bằng mvn install -DskipTests
- [ ] Verify độc lập trước khi nối chéo

## 13. Kết luận ngắn

Muốn các phân hệ độc lập thật sự, anh nên tách theo bounded context:

- danh mục là shared master data
- văn bản đến, văn bản đi, dự thảo là document core nhưng tách bảng và lifecycle riêng
- hồ sơ công việc, lịch công tác, theo dõi nhiệm vụ là orchestration modules, chỉ liên kết bằng ID

Nếu làm đúng ngay từ đầu, sau này thêm workflow, Elasticsearch, thống kê, data scope và phân quyền sẽ dễ hơn rất nhiều.