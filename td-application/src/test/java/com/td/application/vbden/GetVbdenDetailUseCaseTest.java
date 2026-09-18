package com.td.application.vbden;

import com.td.application.common.models.PaginationResponse;
import com.td.application.common.models.Result;
import com.td.application.common.security.CurrentUserProvider;
import com.td.application.documents.DocumentRepository;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.application.documents.FileDto;
import com.td.application.documents.GetDynamicDocumentUseCase;
import com.td.application.documents.ListDocumentFilesUseCase;
import com.td.application.documents.VbdenOrgViewFilter;
import com.td.application.sharedcore.AppUserRepository;
import com.td.domain.documents.BusinessDocument;
import com.td.domain.sharedcore.AppUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * BL-210 (VBDen Core) — DoD (3): xem chi tiết chỉ trả về document thực sự là văn bản đến
 * ({@code databasetype=vbden} — module discriminator nội bộ, xem Bugfix 24/08/2026 phía dưới,
 * KHÔNG phải field nghiệp vụ {@code document_type} "Loại văn bản"), dù {@link GetDynamicDocumentUseCase}
 * (BL-101) tìm thấy document hợp lệ thuộc module khác.
 *
 * <p>Bugfix 24/08/2026 (người dùng phát hiện): response từng trả "document_type": "vbden" dù field
 * nghiệp vụ "Loại văn bản" đã lưu đúng vào dynamic_fields — do 2 chỗ trong lớp này {@code put()}
 * (không phải {@code putIfAbsent}) giá trị module discriminator SAU khi merge dynamic_fields, đè mất
 * giá trị nghiệp vụ thực. Đổi key sang {@code databasetype} (khớp thuật ngữ Domino thật —
 * {@code qlvb/add?databasetype=vbden}) để tách biệt hoàn toàn 2 khái niệm.</p>
 *
 * <p>Bugfix 21/08/2026 (Slice 9): thêm test cho nhánh {@code idso} (join với văn bản gốc qua bản
 * ghi "sổ", kiến trúc 2-document — xem javadoc {@link GetVbdenDetailUseCase}).</p>
 *
 * <p>Bugfix 21/08/2026 (lần 2): thêm test cho nhánh "phẳng" ({@code idso} rỗng) — xoá trắng
 * document_number/document_register* theo mặc định (tránh lộ số của tổ chức khác), điền lại khi
 * {@code vt}/{@code edit} tìm thấy bản ghi sổ khớp tổ chức, và đánh dấu "đã xem để biết"
 * ({@code user_to_read} → {@code user_to_readed}).</p>
 */
@ExtendWith(MockitoExtension.class)
class GetVbdenDetailUseCaseTest {

    @Mock
    private GetDynamicDocumentUseCase getDynamicDocumentUseCase;
    @Mock
    private DocumentRepository documentRepository;
    @Mock
    private com.td.application.vbden.XinYKienDocumentRepository xinYKienDocumentRepository;
    @Mock
    private CurrentUserProvider currentUserProvider;
    @Mock
    private AppUserRepository appUserRepository;
    @Mock
    private ListDocumentFilesUseCase listDocumentFilesUseCase;
    @Mock
    private com.td.application.documents.AuthorAclFieldsService authorAclFieldsService;

    private final DynamicDocumentFieldsService dynamicDocumentFieldsService = new DynamicDocumentFieldsService();

    private GetVbdenDetailUseCase useCase;

    @Test
    void execute_withVbdenDocument_shouldReturnFields() {
        useCase = newUseCase();
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("databasetype", "vbden");
        fields.put("subject", "Van ban den test");
        when(getDynamicDocumentUseCase.execute(any())).thenReturn(Result.success(fields));

        var request = new GetVbdenDetailRequest();
        request.setIdvbden(UUID.randomUUID().toString());
        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).containsEntry("subject", "Van ban den test");
    }

    @Test
    void execute_withNonVbdenDocument_shouldReturnFailure() {
        useCase = newUseCase();
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("databasetype", "vbdi");
        when(getDynamicDocumentUseCase.execute(any())).thenReturn(Result.success(fields));

        var request = new GetVbdenDetailRequest();
        request.setIdvbden(UUID.randomUUID().toString());
        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getError()).isEqualTo("Không tìm thấy văn bản đến");
    }

    // N08 (31/08/2026 - "cho y kien"): ItemsTVUB.jsx goi CHUNG XemChiTiet cho "van ban den gia"
    // (document_type="vbden_xinykien") - nhung loai nay song o BANG RIENG vbden_xinykien_documents
    // (yeu cau nguoi dung "tao bang rieng") nen GetDynamicDocumentUseCase (chi biet bang chung) LUON
    // that bai truoc - executePlainDocument phai FALLBACK sang xinYKienDocumentRepository.
    @Test
    void execute_withXinYkienDocument_shouldReturnFields() {
        useCase = newUseCase();
        UUID id = UUID.randomUUID();
        when(getDynamicDocumentUseCase.execute(any())).thenReturn(Result.failure("Document not found with ID: " + id));
        com.td.domain.vbden.XinYKienDocument document = new com.td.domain.vbden.XinYKienDocument(
            "Van ban goc", "vbden_xinykien", "0", null, null, null, null);
        document.replaceDynamicFields("{\"subject\":\"Xin y kien TVUB\"}");
        when(xinYKienDocumentRepository.findById(any())).thenReturn(java.util.Optional.of(document));

        var request = new GetVbdenDetailRequest();
        request.setIdvbden(id.toString());
        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).containsEntry("subject", "Xin y kien TVUB");
        assertThat(result.getData()).containsEntry("databasetype", "vbden_xinykien");
    }

    @Test
    void execute_withDeletedXinYkienDocument_shouldReturnFailure() {
        useCase = newUseCase();
        UUID id = UUID.randomUUID();
        when(getDynamicDocumentUseCase.execute(any())).thenReturn(Result.failure("Document not found with ID: " + id));
        com.td.domain.vbden.XinYKienDocument document = new com.td.domain.vbden.XinYKienDocument(
            "Van ban goc", "vbden_xinykien", "0", null, null, null, null);
        document.markAsDeleted(null);
        when(xinYKienDocumentRepository.findById(any())).thenReturn(java.util.Optional.of(document));

        var request = new GetVbdenDetailRequest();
        request.setIdvbden(id.toString());
        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    void execute_whenUnderlyingUseCaseFails_shouldPropagateFailure() {
        useCase = newUseCase();
        UUID id = UUID.randomUUID();
        when(getDynamicDocumentUseCase.execute(any())).thenReturn(Result.failure("Document not found with ID: " + id));

        var request = new GetVbdenDetailRequest();
        request.setIdvbden(id.toString());

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getError()).contains(id.toString());
    }

    // Bugfix 21/08/2026 (lan 2): 1 van ban goc co the duoc NHIEU to chuc "vao so" doc lap - khong
    // duoc lo document_number cua to chuc khac cho nguoi xem khong lien quan, mac dinh phai rong.
    @Test
    void execute_plainDocument_shouldBlankDocumentRegisterFieldsByDefault() {
        useCase = newUseCase();
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("databasetype", "vbden");
        fields.put("document_number", "3");
        fields.put("document_register", "So van thu");
        fields.put("document_register_id", UUID.randomUUID().toString());
        when(getDynamicDocumentUseCase.execute(any())).thenReturn(Result.success(fields));

        var request = new GetVbdenDetailRequest();
        request.setIdvbden(UUID.randomUUID().toString());
        // edit/vt khong duoc gui - khong tra vbden_so, phai xoa trang.
        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).containsEntry("document_number", "");
        assertThat(result.getData()).containsEntry("document_register", "");
        assertThat(result.getData()).containsEntry("document_register_id", "");
    }

    @Test
    void execute_plainDocument_withVtTrueAndMatchingRegisterEntry_shouldFillFromEntry() {
        useCase = newUseCase();
        UUID parentId = UUID.randomUUID();
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("databasetype", "vbden");
        fields.put("document_number", "");
        when(getDynamicDocumentUseCase.execute(any())).thenReturn(Result.success(fields));
        when(documentRepository.findById(parentId)).thenReturn(Optional.empty()); // khong anh huong "danh dau da xem"

        AppUser user = newUserWithIdentifier("PHONG_TH");
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("vanthu01"));
        when(appUserRepository.findByUsernameAndDeletedOnIsNull("vanthu01")).thenReturn(Optional.of(user));

        BusinessDocument entry = newDocument("vbden_so",
            "{\"parentid\":\"" + parentId + "\",\"document_number\":\"7\",\"document_register\":\"So A\"}");
        ArgumentCaptor<VbdenOrgViewFilter> filterCaptor = ArgumentCaptor.forClass(VbdenOrgViewFilter.class);
        when(documentRepository.findByVbdenOrgView(eq("vbden_so"), filterCaptor.capture(), any()))
            .thenReturn(new PageImpl<>(List.of(entry), PageRequest.of(0, 1), 1));

        var request = new GetVbdenDetailRequest();
        request.setIdvbden(parentId.toString());
        request.setVt(true);

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).containsEntry("document_number", "7");
        assertThat(result.getData()).containsEntry("document_register", "So A");
        assertThat(result.getData()).containsEntry("idso", entry.getId());
        assertThat(filterCaptor.getValue().getOrgValue()).isEqualTo("PHONG_TH");
        assertThat(filterCaptor.getValue().getIncludeFieldValues()).containsEntry("parentid", List.of(parentId.toString()));
    }

    @Test
    void execute_plainDocument_withEditTrueButNoMatchingRegisterEntry_shouldStayBlank() {
        useCase = newUseCase();
        UUID parentId = UUID.randomUUID();
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("databasetype", "vbden");
        when(getDynamicDocumentUseCase.execute(any())).thenReturn(Result.success(fields));
        when(documentRepository.findById(parentId)).thenReturn(Optional.empty());

        AppUser user = newUserWithIdentifier("PHONG_TH");
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("vanthu01"));
        when(appUserRepository.findByUsernameAndDeletedOnIsNull("vanthu01")).thenReturn(Optional.of(user));
        when(documentRepository.findByVbdenOrgView(eq("vbden_so"), any(), any()))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 1), 0));

        var request = new GetVbdenDetailRequest();
        request.setIdvbden(parentId.toString());
        request.setEdit("true");

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).containsEntry("document_number", "");
        assertThat(result.getData()).doesNotContainKey("idso");
    }

    @Test
    void execute_plainDocument_withoutVtOrEdit_shouldNotQueryRegisterEntry() {
        useCase = newUseCase();
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("databasetype", "vbden");
        when(getDynamicDocumentUseCase.execute(any())).thenReturn(Result.success(fields));
        when(documentRepository.findById(any())).thenReturn(Optional.empty());

        var request = new GetVbdenDetailRequest();
        request.setIdvbden(UUID.randomUUID().toString());
        request.setVt(false);

        useCase.execute(request);

        verify(documentRepository, never()).findByVbdenOrgView(any(), any(), any());
    }

    // Bugfix 21/08/2026 (lan 2): user_to_read -> user_to_readed - co che DUY NHAT chuyen van ban
    // giua view xemdebiet/daxemdebiet (da port o ViewQueryService).
    @Test
    void execute_plainDocument_shouldMarkUserToReadAsReaded_whenUsernamePending() {
        useCase = newUseCase();
        BusinessDocument parent = newDocument("vbden",
            "{\"databasetype\":\"vbden\",\"user_to_read\":[\"vanthu01\",\"vanthu02\"]}");
        UUID id = parent.getId();
        Map<String, Object> fields = dynamicDocumentFieldsService.parseDynamicFields(parent.getDynamicFieldsJson());
        when(getDynamicDocumentUseCase.execute(any())).thenReturn(Result.success(fields));
        when(documentRepository.findById(id)).thenReturn(Optional.of(parent));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("vanthu01"));

        var request = new GetVbdenDetailRequest();
        request.setIdvbden(id.toString());

        useCase.execute(request);

        ArgumentCaptor<BusinessDocument> savedCaptor = ArgumentCaptor.forClass(BusinessDocument.class);
        verify(documentRepository).save(savedCaptor.capture());
        Map<String, Object> savedFields = dynamicDocumentFieldsService.parseDynamicFields(savedCaptor.getValue().getDynamicFieldsJson());
        assertThat(savedFields.get("user_to_read")).isEqualTo(List.of("vanthu02"));
        assertThat(savedFields.get("user_to_readed")).isEqualTo(List.of("vanthu01"));
    }

    // Bugfix 22/08/2026 (cung dang D.14/R4): user_to_read co the duoc ghi dang SCALAR TEXT
    // (";"-joined) qua TiepNhan/Luu (sau khi sua DonViNguoiNhanXuLy.jsx de cung ghi field nay) -
    // truoc day chi hieu List nen se im lang KHONG danh dau duoc "da xem" cho van ban tao/hieu
    // chinh qua luong do.
    @Test
    void execute_plainDocument_shouldMarkUserToReadAsReaded_whenStoredAsScalarString() {
        useCase = newUseCase();
        BusinessDocument parent = newDocument("vbden",
            "{\"databasetype\":\"vbden\",\"user_to_read\":\"vanthu01;vanthu02\"}");
        UUID id = parent.getId();
        Map<String, Object> fields = dynamicDocumentFieldsService.parseDynamicFields(parent.getDynamicFieldsJson());
        when(getDynamicDocumentUseCase.execute(any())).thenReturn(Result.success(fields));
        when(documentRepository.findById(id)).thenReturn(Optional.of(parent));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("vanthu01"));

        var request = new GetVbdenDetailRequest();
        request.setIdvbden(id.toString());

        useCase.execute(request);

        ArgumentCaptor<BusinessDocument> savedCaptor = ArgumentCaptor.forClass(BusinessDocument.class);
        verify(documentRepository).save(savedCaptor.capture());
        Map<String, Object> savedFields = dynamicDocumentFieldsService.parseDynamicFields(savedCaptor.getValue().getDynamicFieldsJson());
        assertThat(savedFields.get("user_to_read")).isEqualTo(List.of("vanthu02"));
        assertThat(savedFields.get("user_to_readed")).isEqualTo(List.of("vanthu01"));
    }

    // Bugfix 18/09/2026 (xem javadoc lop muc "Nhat ky da xem") - truoc day KHONG luu khi username
    // khong nam trong user_to_read; gio VAN luu (vi appendLogReadEntry doc lap voi nhanh user_to_read)
    // nhung KHONG dung user_to_read/user_to_readed.
    @Test
    void execute_plainDocument_shouldNotTouchUserToRead_whenUsernameNotPending() {
        useCase = newUseCase();
        BusinessDocument parent = newDocument("vbden",
            "{\"databasetype\":\"vbden\",\"user_to_read\":[\"vanthu02\"]}");
        UUID id = parent.getId();
        Map<String, Object> fields = dynamicDocumentFieldsService.parseDynamicFields(parent.getDynamicFieldsJson());
        when(getDynamicDocumentUseCase.execute(any())).thenReturn(Result.success(fields));
        when(documentRepository.findById(id)).thenReturn(Optional.of(parent));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("vanthu01"));

        var request = new GetVbdenDetailRequest();
        request.setIdvbden(id.toString());

        useCase.execute(request);

        ArgumentCaptor<BusinessDocument> savedCaptor = ArgumentCaptor.forClass(BusinessDocument.class);
        verify(documentRepository).save(savedCaptor.capture());
        Map<String, Object> savedFields = dynamicDocumentFieldsService.parseDynamicFields(savedCaptor.getValue().getDynamicFieldsJson());
        assertThat(savedFields.get("user_to_read")).isEqualTo(List.of("vanthu02"));
        assertThat(savedFields.get("user_to_readed")).isNull();
    }

    // Bo sung 18/09/2026 (nguoi dung bao "Nhat ky da xem" - LogReadCollapse.jsx - dang trong): mirror
    // XemChiTiet.java#markDocumentViewedFlat that - lan xem DAU TIEN cua 1 username phai duoc ghi vao
    // log_read_obj dang "username|dd/MM/yyyy HH:mm:ss", KE CA khi username khong nam trong
    // user_to_read (da xac nhan o test tren).
    @Test
    void execute_plainDocument_shouldAppendLogReadEntry_onFirstView() {
        useCase = newUseCase();
        BusinessDocument parent = newDocument("vbden", "{\"databasetype\":\"vbden\"}");
        UUID id = parent.getId();
        Map<String, Object> fields = dynamicDocumentFieldsService.parseDynamicFields(parent.getDynamicFieldsJson());
        when(getDynamicDocumentUseCase.execute(any())).thenReturn(Result.success(fields));
        when(documentRepository.findById(id)).thenReturn(Optional.of(parent));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("xuanlt.skhcn"));

        var request = new GetVbdenDetailRequest();
        request.setIdvbden(id.toString());

        useCase.execute(request);

        ArgumentCaptor<BusinessDocument> savedCaptor = ArgumentCaptor.forClass(BusinessDocument.class);
        verify(documentRepository).save(savedCaptor.capture());
        Map<String, Object> savedFields = dynamicDocumentFieldsService.parseDynamicFields(savedCaptor.getValue().getDynamicFieldsJson());
        assertThat((String) savedFields.get("log_read_obj")).matches("xuanlt\\.skhcn\\|\\d{2}/\\d{2}/\\d{4} \\d{2}:\\d{2}:\\d{2}");
    }

    // Dedupe THEO USERNAME (dung Domino goc): lan xem THU HAI cua CUNG 1 nguoi KHONG ghi them dong
    // moi - khong con field nao thay doi nen KHONG save luon (khac voi lan dau).
    @Test
    void execute_plainDocument_shouldNotDuplicateLogReadEntry_onSecondViewBySameUser() {
        useCase = newUseCase();
        BusinessDocument parent = newDocument("vbden",
            "{\"databasetype\":\"vbden\",\"log_read_obj\":\"xuanlt.skhcn|18/09/2026 08:00:00\"}");
        UUID id = parent.getId();
        Map<String, Object> fields = dynamicDocumentFieldsService.parseDynamicFields(parent.getDynamicFieldsJson());
        when(getDynamicDocumentUseCase.execute(any())).thenReturn(Result.success(fields));
        when(documentRepository.findById(id)).thenReturn(Optional.of(parent));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("xuanlt.skhcn"));

        var request = new GetVbdenDetailRequest();
        request.setIdvbden(id.toString());

        useCase.execute(request);

        verify(documentRepository, never()).save(any());
    }

    // Xac nhan field khac dedupe rieng: nguoi MOI xem lan dau van duoc noi them (";" - joined), giu
    // nguyen dong cu.
    @Test
    void execute_plainDocument_shouldAppendLogReadEntry_forNewUser_keepingExistingEntries() {
        useCase = newUseCase();
        BusinessDocument parent = newDocument("vbden",
            "{\"databasetype\":\"vbden\",\"log_read_obj\":\"xuanlt.skhcn|18/09/2026 08:00:00\"}");
        UUID id = parent.getId();
        Map<String, Object> fields = dynamicDocumentFieldsService.parseDynamicFields(parent.getDynamicFieldsJson());
        when(getDynamicDocumentUseCase.execute(any())).thenReturn(Result.success(fields));
        when(documentRepository.findById(id)).thenReturn(Optional.of(parent));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("hoangnt.skhcn"));

        var request = new GetVbdenDetailRequest();
        request.setIdvbden(id.toString());

        useCase.execute(request);

        ArgumentCaptor<BusinessDocument> savedCaptor = ArgumentCaptor.forClass(BusinessDocument.class);
        verify(documentRepository).save(savedCaptor.capture());
        Map<String, Object> savedFields = dynamicDocumentFieldsService.parseDynamicFields(savedCaptor.getValue().getDynamicFieldsJson());
        assertThat((String) savedFields.get("log_read_obj"))
            .startsWith("xuanlt.skhcn|18/09/2026 08:00:00;hoangnt.skhcn|");
    }

    // Bugfix 21/08/2026 (Slice 9): idso co gia tri - phai join voi van ban goc (idvbden), tra ve
    // "id" = id van ban goc, field cua ban ghi so ghi de len field cua van ban goc.
    @Test
    void execute_withIdso_shouldJoinWithParentDocumentAndPreferEntryFields() {
        useCase = newUseCase();
        BusinessDocument parent = newDocument("vbden", "{\"subject\":\"Van ban goc\",\"document_number\":\"\"}");
        UUID parentId = parent.getId();
        BusinessDocument entry = newDocument("vbden_so",
            "{\"document_number\":\"3\",\"parentid\":\"" + parentId + "\"}");
        UUID entryId = entry.getId();
        when(documentRepository.findById(entryId)).thenReturn(Optional.of(entry));
        when(documentRepository.findById(parentId)).thenReturn(Optional.of(parent));

        var request = new GetVbdenDetailRequest();
        request.setIdso(entryId.toString());
        request.setIdvbden(parentId.toString());

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).containsEntry("subject", "Van ban goc");
        assertThat(result.getData()).containsEntry("document_number", "3"); // entry de len parent
        assertThat(result.getData()).containsEntry("id", parentId); // id van ban goc, khong phai id ban ghi so
        assertThat(result.getData()).containsEntry("databasetype", "vbden");
    }

    // Bo sung 14/09/2026 (xem javadoc XoaVanBanDenUseCase/PhucHoiVanBanDenUseCase#putDeletionInfo) -
    // FE (VBDenButtonAction.jsx) can "isDeleted" de quyet dinh hien nut "Phuc hoi"/dong chu "da bi xoa".
    @Test
    void execute_withIdso_notDeleted_shouldMarkIsDeletedFalseWithoutDeletionFields() {
        useCase = newUseCase();
        BusinessDocument parent = newDocument("vbden", "{\"subject\":\"Van ban goc\"}");
        UUID parentId = parent.getId();
        BusinessDocument entry = newDocument("vbden_so", "{\"parentid\":\"" + parentId + "\"}");
        UUID entryId = entry.getId();
        when(documentRepository.findById(entryId)).thenReturn(Optional.of(entry));
        when(documentRepository.findById(parentId)).thenReturn(Optional.of(parent));

        var request = new GetVbdenDetailRequest();
        request.setIdso(entryId.toString());
        request.setIdvbden(parentId.toString());
        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).containsEntry("isDeleted", false);
        assertThat(result.getData()).doesNotContainKey("delete_date");
        assertThat(result.getData()).doesNotContainKey("delete_people_fullname");
    }

    @Test
    void execute_withIdso_parentDeleted_shouldIncludeDeletionInfo() {
        useCase = newUseCase();
        BusinessDocument parent = newDocument("vbden", "{\"subject\":\"Van ban goc\"}");
        UUID deletedBy = UUID.randomUUID();
        parent.markAsDeleted(deletedBy);
        UUID parentId = parent.getId();
        BusinessDocument entry = newDocument("vbden_so", "{\"parentid\":\"" + parentId + "\"}");
        UUID entryId = entry.getId();
        when(documentRepository.findById(entryId)).thenReturn(Optional.of(entry));
        when(documentRepository.findById(parentId)).thenReturn(Optional.of(parent));
        AppUser deleter = newUserWithIdentifier("PHONG_TH");
        when(appUserRepository.findById(deletedBy)).thenReturn(Optional.of(deleter));

        var request = new GetVbdenDetailRequest();
        request.setIdso(entryId.toString());
        request.setIdvbden(parentId.toString());
        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).containsEntry("isDeleted", true);
        assertThat((String) result.getData().get("delete_date")).matches("\\d{2}/\\d{2}/\\d{4} \\d{2}:\\d{2}:\\d{2}");
        assertThat(result.getData()).containsEntry("delete_people_fullname", "Ten day du");
    }

    @Test
    void execute_withIdsoButEntryNotFound_shouldReturnFailure() {
        useCase = newUseCase();
        UUID entryId = UUID.randomUUID();
        when(documentRepository.findById(entryId)).thenReturn(Optional.empty());

        var request = new GetVbdenDetailRequest();
        request.setIdso(entryId.toString());

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getError()).isEqualTo("Không tìm thấy văn bản đến");
    }

    @Test
    void execute_withIdsoButParentNotFound_shouldReturnFailure() {
        useCase = newUseCase();
        BusinessDocument entry = newDocument("vbden_so", "{}");
        UUID entryId = entry.getId();
        UUID parentId = UUID.randomUUID();
        when(documentRepository.findById(entryId)).thenReturn(Optional.of(entry));
        when(documentRepository.findById(parentId)).thenReturn(Optional.empty());

        var request = new GetVbdenDetailRequest();
        request.setIdso(entryId.toString());
        request.setIdvbden(parentId.toString());

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getError()).isEqualTo("Không tìm thấy văn bản đến");
    }

    @Test
    void execute_withIdsoButEntryNotRegisterEntryType_shouldReturnFailure() {
        useCase = newUseCase();
        BusinessDocument entry = newDocument("vbden", "{}");
        when(documentRepository.findById(entry.getId())).thenReturn(Optional.of(entry));

        var request = new GetVbdenDetailRequest();
        request.setIdso(entry.getId().toString());

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getError()).isEqualTo("Không tìm thấy văn bản đến");
    }

    // Bugfix 22/08/2026 (bao loi nguoi dung: "chua thay hien thi trich yeu"): TiepNhanVbdenUseCase
    // luu "subject" (trich yeu) vao COT entity.title, KHONG vao dynamic_fields -> van ban tao qua
    // Java khong co "subject" trong dynamic_fields, FE doc resdata.subject se rong. Phai fallback
    // ve entity.title khi dynamic_fields chua co "subject".
    @Test
    void execute_plainDocument_withoutSubjectInDynamicFields_shouldFallBackToEntityTitle() {
        useCase = newUseCase();
        BusinessDocument parent = newDocument("vbden", "{\"databasetype\":\"vbden\"}"); // title = "Tieu de"
        UUID id = parent.getId();
        Map<String, Object> fields = dynamicDocumentFieldsService.parseDynamicFields(parent.getDynamicFieldsJson());
        when(getDynamicDocumentUseCase.execute(any())).thenReturn(Result.success(fields));
        when(documentRepository.findById(id)).thenReturn(Optional.of(parent));

        var request = new GetVbdenDetailRequest();
        request.setIdvbden(id.toString());

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).containsEntry("subject", "Tieu de");
    }

    // Van ban DA duoc hieu chinh qua Luu() (dynamic_fields co "subject" rieng, co the da lech khoi
    // title goc) -> giu dung gia tri da luu, KHONG de bang title.
    @Test
    void execute_plainDocument_withExistingSubjectInDynamicFields_shouldNotOverride() {
        useCase = newUseCase();
        BusinessDocument parent = newDocument("vbden",
            "{\"databasetype\":\"vbden\",\"subject\":\"Trich yeu da sua\"}");
        UUID id = parent.getId();
        Map<String, Object> fields = dynamicDocumentFieldsService.parseDynamicFields(parent.getDynamicFieldsJson());
        when(getDynamicDocumentUseCase.execute(any())).thenReturn(Result.success(fields));
        when(documentRepository.findById(id)).thenReturn(Optional.of(parent));

        var request = new GetVbdenDetailRequest();
        request.setIdvbden(id.toString());

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).containsEntry("subject", "Trich yeu da sua");
    }

    // Nhanh idso (join voi van ban goc) cung phai fallback tuong tu.
    @Test
    void execute_withIdso_withoutSubjectInDynamicFields_shouldFallBackToEntityTitle() {
        useCase = newUseCase();
        BusinessDocument parent = newDocument("vbden", "{\"document_number\":\"\"}"); // title = "Tieu de"
        UUID parentId = parent.getId();
        BusinessDocument entry = newDocument("vbden_so",
            "{\"document_number\":\"3\",\"parentid\":\"" + parentId + "\"}");
        UUID entryId = entry.getId();
        when(documentRepository.findById(entryId)).thenReturn(Optional.of(entry));
        when(documentRepository.findById(parentId)).thenReturn(Optional.of(parent));

        var request = new GetVbdenDetailRequest();
        request.setIdso(entryId.toString());
        request.setIdvbden(parentId.toString());

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).containsEntry("subject", "Tieu de");
    }

    // "port them tep dinh kem cho van ban den" 30/08/2026: "files" phai xuat hien trong response,
    // dung shape {name,size,type,file_id} Files/filesVBden.jsx dang doc (resdata?.files) - dung tien
    // le GetVbdiDetailUseCaseTest#execute_withFiles_shouldIncludeFilesInResponse.
    @Test
    void execute_plainDocument_withFiles_shouldIncludeFilesInResponse() {
        useCase = newUseCase();
        BusinessDocument parent = newDocument("vbden", "{\"databasetype\":\"vbden\"}");
        UUID id = parent.getId();
        Map<String, Object> fields = dynamicDocumentFieldsService.parseDynamicFields(parent.getDynamicFieldsJson());
        when(getDynamicDocumentUseCase.execute(any())).thenReturn(Result.success(fields));
        when(documentRepository.findById(id)).thenReturn(Optional.of(parent));
        UUID fileId = UUID.randomUUID();
        var fileDto = FileDto.builder()
            .fileId(fileId)
            .originalFileName("bao_cao.docx")
            .fileSize(1024L)
            .build();
        when(listDocumentFilesUseCase.execute(id))
            .thenReturn(new PaginationResponse<>(List.of(fileDto), 0, 20, 1L, 1, true, true));

        var request = new GetVbdenDetailRequest();
        request.setIdvbden(id.toString());
        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> files = (List<Map<String, Object>>) result.getData().get("files");
        assertThat(files).hasSize(1);
        assertThat(files.get(0))
            .containsEntry("name", "bao_cao.docx")
            .containsEntry("size", 1024L)
            .containsEntry("type", "docx")
            .containsEntry("file_id", fileId);
    }

    // Nhanh idso (join voi van ban goc): file dinh kem phai tra theo id VAN BAN GOC (parentId), khong
    // phai id ban ghi so (entryId) - xem javadoc lop.
    @Test
    void execute_withIdso_withFiles_shouldCollectFilesByParentDocumentId() {
        useCase = newUseCase();
        BusinessDocument parent = newDocument("vbden", "{\"subject\":\"Van ban goc\"}");
        UUID parentId = parent.getId();
        BusinessDocument entry = newDocument("vbden_so", "{\"parentid\":\"" + parentId + "\"}");
        UUID entryId = entry.getId();
        when(documentRepository.findById(entryId)).thenReturn(Optional.of(entry));
        when(documentRepository.findById(parentId)).thenReturn(Optional.of(parent));
        UUID fileId = UUID.randomUUID();
        var fileDto = FileDto.builder().fileId(fileId).originalFileName("phu_luc.pdf").fileSize(2048L).build();
        when(listDocumentFilesUseCase.execute(parentId))
            .thenReturn(new PaginationResponse<>(List.of(fileDto), 0, 20, 1L, 1, true, true));

        var request = new GetVbdenDetailRequest();
        request.setIdso(entryId.toString());
        request.setIdvbden(parentId.toString());
        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> files = (List<Map<String, Object>>) result.getData().get("files");
        assertThat(files).hasSize(1);
        assertThat(files.get(0)).containsEntry("file_id", fileId).containsEntry("name", "phu_luc.pdf");
        verify(listDocumentFilesUseCase, never()).execute(entryId);
    }

    // Bugfix 07/09/2026 (nguoi dung bao loi hien thi "Van ban den qua mang ngay undefined") - xem
    // javadoc lop. Ca 3 nhanh (phang/idso/xinykien) deu phai co "created" (dinh dang
    // "dd/MM/yyyy HH:mm:ss", KHAC "created_on" khong dinh dang co san).
    @Test
    void execute_plainDocument_shouldIncludeFormattedCreatedField() {
        useCase = newUseCase();
        BusinessDocument parent = newDocument("vbden", "{\"databasetype\":\"vbden\"}");
        UUID id = parent.getId();
        Map<String, Object> fields = dynamicDocumentFieldsService.parseDynamicFields(parent.getDynamicFieldsJson());
        when(getDynamicDocumentUseCase.execute(any())).thenReturn(Result.success(fields));
        when(documentRepository.findById(id)).thenReturn(Optional.of(parent));

        var request = new GetVbdenDetailRequest();
        request.setIdvbden(id.toString());
        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        assertThat((String) result.getData().get("created")).matches("\\d{2}/\\d{2}/\\d{4} \\d{2}:\\d{2}:\\d{2}");
    }

    @Test
    void execute_withIdso_shouldIncludeFormattedCreatedField() {
        useCase = newUseCase();
        BusinessDocument parent = newDocument("vbden", "{\"subject\":\"Van ban goc\"}");
        UUID parentId = parent.getId();
        BusinessDocument entry = newDocument("vbden_so", "{\"parentid\":\"" + parentId + "\"}");
        UUID entryId = entry.getId();
        when(documentRepository.findById(entryId)).thenReturn(Optional.of(entry));
        when(documentRepository.findById(parentId)).thenReturn(Optional.of(parent));

        var request = new GetVbdenDetailRequest();
        request.setIdso(entryId.toString());
        request.setIdvbden(parentId.toString());
        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        assertThat((String) result.getData().get("created")).matches("\\d{2}/\\d{2}/\\d{4} \\d{2}:\\d{2}:\\d{2}");
    }

    @Test
    void execute_withXinYkienDocument_shouldIncludeFormattedCreatedField() {
        useCase = newUseCase();
        UUID id = UUID.randomUUID();
        when(getDynamicDocumentUseCase.execute(any())).thenReturn(Result.failure("Document not found with ID: " + id));
        com.td.domain.vbden.XinYKienDocument document = new com.td.domain.vbden.XinYKienDocument(
            "Van ban goc", "vbden_xinykien", "0", null, null, null, null);
        when(xinYKienDocumentRepository.findById(any())).thenReturn(java.util.Optional.of(document));

        var request = new GetVbdenDetailRequest();
        request.setIdvbden(id.toString());
        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        assertThat((String) result.getData().get("created")).matches("\\d{2}/\\d{2}/\\d{4} \\d{2}:\\d{2}:\\d{2}");
    }

    private GetVbdenDetailUseCase newUseCase() {
        // 30/08/2026 ("port them tep dinh kem cho van ban den"): GetVbdenDetailUseCase gio luon goi
        // listDocumentFilesUseCase (collectFiles) - stub mac dinh trang rong (lenient, khong lam cac
        // test khac quan tam "files" bi NPE); test rieng execute_..._shouldIncludeFilesInResponse ghi
        // de lai - dung tien le GetVbdiDetailUseCaseTest#stubEmptyFiles.
        lenient().when(listDocumentFilesUseCase.execute(any()))
            .thenReturn(new PaginationResponse<>(List.of(), 0, 20, 0L, 0, true, true));
        return new GetVbdenDetailUseCase(getDynamicDocumentUseCase, documentRepository, xinYKienDocumentRepository,
            dynamicDocumentFieldsService, currentUserProvider, appUserRepository, listDocumentFilesUseCase,
            authorAclFieldsService);
    }

    private BusinessDocument newDocument(String documentType, String dynamicFieldsJson) {
        BusinessDocument document = new BusinessDocument("Tieu de", documentType, "0", null, null, null, null);
        return document.replaceDynamicFields(dynamicFieldsJson);
    }

    private AppUser newUserWithIdentifier(String identifier) {
        return new AppUser("kc-vanthu01", "vanthu01", "Ten day du", null, null, null, true,
            null, 0, null, null, null, null, null, null, identifier, false, null);
    }
}
