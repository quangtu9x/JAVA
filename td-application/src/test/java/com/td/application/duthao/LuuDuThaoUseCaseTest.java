package com.td.application.duthao;

import com.td.application.common.security.CurrentUserProvider;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.domain.duthao.DuThaoDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * BL-230 (DuThao Core) — Slice 1: {@link LuuDuThaoUseCase} — hiệu chỉnh 1 dự thảo ĐÃ TỒN TẠI, chỉ
 * merge field vào {@code dynamic_fields} hiện có + đồng bộ lại {@code title} nếu request gửi
 * {@code subject} (xem javadoc lớp về phạm vi đã port/chưa port).
 */
@ExtendWith(MockitoExtension.class)
class LuuDuThaoUseCaseTest {

    @Mock
    private DuThaoDocumentRepository duThaoDocumentRepository;
    @Mock
    private CurrentUserProvider currentUserProvider;
    @Mock
    private com.td.application.documents.AuthorAclFieldsService authorAclFieldsService;

    private final DynamicDocumentFieldsService dynamicDocumentFieldsService = new DynamicDocumentFieldsService();

    private LuuDuThaoUseCase useCase;

    @Test
    void execute_withoutId_shouldReturnFailure() {
        useCase = newUseCase();
        var result = useCase.execute(new LuuDuThaoRequest());

        assertThat(result.isSuccess()).isFalse();
        verify(duThaoDocumentRepository, never()).save(any());
    }

    @Test
    void execute_documentNotFound_shouldReturnFailure() {
        useCase = newUseCase();
        UUID id = UUID.randomUUID();
        when(duThaoDocumentRepository.findById(id)).thenReturn(Optional.empty());

        var request = new LuuDuThaoRequest();
        request.setId(id);

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getError()).isEqualTo("Không tìm thấy văn bản dự thảo");
    }

    @Test
    void execute_documentWrongType_shouldReturnFailure() {
        useCase = newUseCase();
        DuThaoDocument document = newDocument("vbdi", "{}");
        when(duThaoDocumentRepository.findById(document.getId())).thenReturn(Optional.of(document));

        var request = new LuuDuThaoRequest();
        request.setId(document.getId());

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    void execute_shouldMergeFieldsAndKeepUnrelatedFieldsUnchanged() {
        useCase = newUseCase();
        DuThaoDocument document = newDocument("duthao", "{\"subject\":\"Cu\",\"code\":\"123/QD\"}");
        when(duThaoDocumentRepository.findById(document.getId())).thenReturn(Optional.of(document));

        var request = new LuuDuThaoRequest();
        request.setId(document.getId());
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("unit_in_charge", "So Noi Vu");
        request.setFields(fields);

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).isEqualTo(document.getId());
        Map<String, Object> saved = dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson());
        assertThat(saved).containsEntry("unit_in_charge", "So Noi Vu");
        assertThat(saved).containsEntry("subject", "Cu"); // giu nguyen, khong bi mat
        assertThat(saved).containsEntry("code", "123/QD"); // giu nguyen, khong bi mat
        verify(duThaoDocumentRepository).save(document);
    }

    // TaoDuThaoUseCase luu "subject" vao cot tinh "title" - neu Luu() gui subject moi, phai dong bo
    // lai title de khong bi lech khoi dynamic_fields.subject.
    @Test
    void execute_whenSubjectChanged_shouldSyncEntityTitle() {
        useCase = newUseCase();
        DuThaoDocument document = newDocument("duthao", "{}");
        when(duThaoDocumentRepository.findById(document.getId())).thenReturn(Optional.of(document));

        var request = new LuuDuThaoRequest();
        request.setId(document.getId());
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("subject", "Trich yeu moi");
        request.setFields(fields);

        useCase.execute(request);

        assertThat(document.getTitle()).isEqualTo("Trich yeu moi");
    }

    @Test
    void execute_whenSubjectBlank_shouldNotOverrideTitle() {
        useCase = newUseCase();
        DuThaoDocument document = newDocument("duthao", "{}");
        when(duThaoDocumentRepository.findById(document.getId())).thenReturn(Optional.of(document));

        var request = new LuuDuThaoRequest();
        request.setId(document.getId());
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("unit_in_charge", "So Noi Vu");
        request.setFields(fields);

        useCase.execute(request);

        assertThat(document.getTitle()).isEqualTo("Tieu de");
    }

    // Bugfix 24/08/2026 (bao loi nguoi dung: GetView theo user_creator luon rong voi du thao tao
    // TRUOC bugfix TaoDuThaoUseCase) - backfill "user_creator" khi dang rong/vang mat sau merge.
    @Test
    void execute_whenUserCreatorMissing_shouldBackfillWithCurrentUsername() {
        useCase = newUseCase();
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("nhanvien01"));
        DuThaoDocument document = newDocument("duthao", "{}");
        when(duThaoDocumentRepository.findById(document.getId())).thenReturn(Optional.of(document));

        var request = new LuuDuThaoRequest();
        request.setId(document.getId());
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("unit_in_charge", "So Noi Vu");
        request.setFields(fields);

        useCase.execute(request);

        Map<String, Object> saved = dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson());
        assertThat(saved).containsEntry("user_creator", "nhanvien01");
    }

    @Test
    void execute_whenUserCreatorAlreadyPresent_shouldNotOverrideIt() {
        useCase = newUseCase();
        DuThaoDocument document = newDocument("duthao", "{\"user_creator\":\"nguoitaothat\"}");
        when(duThaoDocumentRepository.findById(document.getId())).thenReturn(Optional.of(document));

        var request = new LuuDuThaoRequest();
        request.setId(document.getId());
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("unit_in_charge", "So Noi Vu");
        request.setFields(fields);

        useCase.execute(request);

        Map<String, Object> saved = dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson());
        // Gia tri co san PHAI duoc giu nguyen - khong bi CurrentUserProvider de len (khong verify
        // never() vi newUseCase() da stub currentUsername(), ban than lenient().when(...) cung tinh
        // la 1 invocation trong Mockito).
        assertThat(saved).containsEntry("user_creator", "nguoitaothat");
    }

    // 26/08/2026 (SERVER-AUTHORITATIVE): client gửi user_creator qua Luu cũng phải bị bỏ qua —
    // giữ nguyên danh tính người tạo đã có trên bản ghi.
    @Test
    void execute_whenClientSendsUserCreator_shouldIgnoreIt_andKeepExistingCreator() {
        useCase = newUseCase();
        DuThaoDocument document = newDocument("duthao", "{\"user_creator\":\"nguoitaothat\"}");
        when(duThaoDocumentRepository.findById(document.getId())).thenReturn(Optional.of(document));

        var request = new LuuDuThaoRequest();
        request.setId(document.getId());
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("unit_in_charge", "So Noi Vu");
        fields.put("user_creator", "skhcn");
        request.setFields(fields);

        useCase.execute(request);

        Map<String, Object> saved = dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson());
        assertThat(saved).containsEntry("user_creator", "nguoitaothat");
    }

    // Bản ghi HỎNG (chưa có creator) + client cố gắn skhcn -> backfill bằng người đang LƯU,
    // không bao giờ nhận giá trị client gửi.
    @Test
    void execute_whenNoExistingCreator_andClientSendsOne_shouldBackfillWithLoggedInUser() {
        useCase = newUseCase();
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("demo1"));
        DuThaoDocument document = newDocument("duthao", "{}");
        when(duThaoDocumentRepository.findById(document.getId())).thenReturn(Optional.of(document));

        var request = new LuuDuThaoRequest();
        request.setId(document.getId());
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("user_creator", "skhcn");
        request.setFields(fields);

        useCase.execute(request);

        Map<String, Object> saved = dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson());
        assertThat(saved).containsEntry("user_creator", "demo1");
    }

    private LuuDuThaoUseCase newUseCase() {
        // lenient(): khong phai test nao cung chay toi backfillUserCreatorIfBlank (vd. cac test
        // failure-path o tren khong luu document).
        lenient().when(currentUserProvider.currentUsername()).thenReturn(Optional.of("system_test"));
        return new LuuDuThaoUseCase(
            duThaoDocumentRepository, dynamicDocumentFieldsService, currentUserProvider, authorAclFieldsService);
    }

    private DuThaoDocument newDocument(String documentType, String dynamicFieldsJson) {
        DuThaoDocument document = new DuThaoDocument("Tieu de", documentType, "0", null, null, null, null);
        return document.replaceDynamicFields(dynamicFieldsJson);
    }
}
