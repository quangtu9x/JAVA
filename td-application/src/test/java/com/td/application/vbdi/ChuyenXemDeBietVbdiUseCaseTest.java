package com.td.application.vbdi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.td.application.common.security.CurrentUserProvider;
import com.td.application.documents.DocumentCacheService;
import com.td.application.documents.DocumentLogService;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.application.sharedcore.UserDisplayInfoService;
import com.td.domain.vbdi.VbdiDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * 14/09/2026 (yêu cầu người dùng "port chức năng ở route vbdi/xuly/nhandebiet như đã xem, chuyển xem
 * để biết, cutoverFE luôn") — xem javadoc {@link ChuyenXemDeBietVbdiUseCase}.
 */
@ExtendWith(MockitoExtension.class)
class ChuyenXemDeBietVbdiUseCaseTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Mock
    private VbdiDocumentRepository vbdiDocumentRepository;
    @Mock
    private DocumentCacheService documentCacheService;
    @Mock
    private DocumentLogService documentLogService;
    @Mock
    private CurrentUserProvider currentUserProvider;
    @Mock
    private UserDisplayInfoService userDisplayInfoService;
    @Mock
    private com.td.application.documents.AuthorAclFieldsService authorAclFieldsService;

    private final DynamicDocumentFieldsService dynamicDocumentFieldsService = new DynamicDocumentFieldsService();
    private ChuyenXemDeBietVbdiUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new ChuyenXemDeBietVbdiUseCase(
            vbdiDocumentRepository, dynamicDocumentFieldsService, documentCacheService, documentLogService,
            currentUserProvider, userDisplayInfoService, authorAclFieldsService);
        lenient().when(vbdiDocumentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(currentUserProvider.currentUsername()).thenReturn(Optional.of("vanthu01"));
        lenient().when(userDisplayInfoService.resolve("vanthu01"))
            .thenReturn(new UserDisplayInfoService.UserDisplayInfo("Nguyen Van Thu", "So KH&CN"));
    }

    private VbdiDocument vbdiDocument() {
        return new VbdiDocument("Van ban di", "vbdi", VbdiStatus.ISSUED, null, null, null, null);
    }

    private VbdiForwardReadRecipient recipient(String id, String name, String username) {
        var r = new VbdiForwardReadRecipient();
        r.setId(id);
        r.setName(name);
        r.setUsername(username);
        return r;
    }

    private VbdiForwardReadRecipient unit(String id, String name, String identifier) {
        var r = new VbdiForwardReadRecipient();
        r.setId(id);
        r.setName(name);
        r.setIdentifier(identifier);
        return r;
    }

    private Map<String, Object> fieldsOf(VbdiDocument document) {
        return dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson());
    }

    @Test
    void execute_withoutDocumentId_shouldFail() {
        var result = useCase.execute(new ChuyenXemDeBietVbdiRequest());
        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    void execute_withoutAnyRecipient_shouldFail() {
        var request = new ChuyenXemDeBietVbdiRequest();
        request.setDocumentId(UUID.randomUUID());
        var result = useCase.execute(request);
        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    void execute_whenDocumentMissing_shouldFail() {
        UUID id = UUID.randomUUID();
        when(vbdiDocumentRepository.findById(id)).thenReturn(Optional.empty());

        var request = new ChuyenXemDeBietVbdiRequest();
        request.setDocumentId(id);
        request.setCoordinators(List.of(recipient("u1", "Tran Thi B", "nhanvien02")));

        var result = useCase.execute(request);
        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    void execute_whenDocumentDeleted_shouldFail() {
        var document = vbdiDocument();
        document.markAsDeleted(UUID.randomUUID());
        when(vbdiDocumentRepository.findById(document.getId())).thenReturn(Optional.of(document));

        var request = new ChuyenXemDeBietVbdiRequest();
        request.setDocumentId(document.getId());
        request.setCoordinators(List.of(recipient("u1", "Tran Thi B", "nhanvien02")));

        var result = useCase.execute(request);
        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    void execute_withNewCoordinator_shouldAppendToPendingAndHideAllAndObjSnapshot() {
        var document = vbdiDocument();
        when(vbdiDocumentRepository.findById(document.getId())).thenReturn(Optional.of(document));

        var request = new ChuyenXemDeBietVbdiRequest();
        request.setDocumentId(document.getId());
        request.setOpinion("Gui de biet");
        request.setCoordinators(List.of(recipient("u1", "Tran Thi B", "nhanvien02")));

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        Map<String, Object> fields = fieldsOf(document);
        assertThat(String.valueOf(fields.get("xemdebiet"))).isEqualTo("Tran Thi B");
        assertThat(String.valueOf(fields.get("coordinator_hide_not_seen_rtext"))).isEqualTo("nhanvien02");
        assertThat(String.valueOf(fields.get("coordinator_hide_rtext"))).isEqualTo("nhanvien02");
        assertThat(String.valueOf(fields.get("userxemdebiet_send"))).isEqualTo("vanthu01");

        JsonNode obj = OBJECT_MAPPER.valueToTree(fields.get("xemdebiet_obj_rtext"));
        assertThat(obj.isArray()).isTrue();
        assertThat(obj).hasSize(1);
        JsonNode entry = obj.get(0);
        assertThat(entry.path("username").asText()).isEqualTo("nhanvien02");
        assertThat(entry.path("usercreator").asText()).isEqualTo("Nguyen Van Thu");
        assertThat(entry.path("status").asText()).isEqualTo("Đã chuyển");
        assertThat(entry.path("detail").get(0).path("content").asText()).isEqualTo("Gui de biet");
    }

    // 30/09/2026: không nhập ý kiến -> câu mặc định CÓ DẤU (hiện ở Nhật ký gửi nhận).
    @Test
    void execute_withoutOpinion_shouldWriteVietnameseDefaultContent() {
        var document = vbdiDocument();
        when(vbdiDocumentRepository.findById(document.getId())).thenReturn(Optional.of(document));

        var request = new ChuyenXemDeBietVbdiRequest();
        request.setDocumentId(document.getId());
        request.setCoordinators(List.of(recipient("u1", "Lê Phú Hà", "lpha")));

        assertThat(useCase.execute(request).isSuccess()).isTrue();
        JsonNode entry = OBJECT_MAPPER.valueToTree(fieldsOf(document).get("xemdebiet_obj_rtext")).get(0);
        assertThat(entry.path("detail").get(0).path("content").asText())
            .startsWith("Nguyen Van Thu chuyển xem để biết cho Lê Phú Hà vào lúc ");
    }

    @Test
    void execute_withInternalUnit_shouldAddIdentifierToPendingButNotHideAll() {
        var document = vbdiDocument();
        when(vbdiDocumentRepository.findById(document.getId())).thenReturn(Optional.of(document));

        var request = new ChuyenXemDeBietVbdiRequest();
        request.setDocumentId(document.getId());
        request.setInternalUnits(List.of(unit("d1", "Phong Ke hoach", "H05.02")));

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        Map<String, Object> fields = fieldsOf(document);
        assertThat(String.valueOf(fields.get("xemdebiet"))).isEqualTo("Phong Ke hoach");
        assertThat(String.valueOf(fields.get("coordinator_hide_not_seen_rtext"))).isEqualTo("H05.02");
        // Domino: filed_UserName1 (coordinator_hide_rtext) CHI duoc set o nhanh "coordinator", KHONG
        // ap dung cho "internal_unit".
        assertThat(fields.get("coordinator_hide_rtext")).isNull();
    }

    @Test
    void execute_whenRecipientAlreadyPending_shouldNotDuplicateObjSnapshot() {
        var document = vbdiDocument();
        document.replaceDynamicFields(
            "{\"coordinator_hide_not_seen_rtext\":\"nhanvien02\",\"xemdebiet\":\"Tran Thi B\","
                + "\"xemdebiet_obj_rtext\":\"[{\\\"username\\\":\\\"nhanvien02\\\",\\\"name\\\":\\\"Tran Thi B\\\"}]\"}");
        when(vbdiDocumentRepository.findById(document.getId())).thenReturn(Optional.of(document));

        var request = new ChuyenXemDeBietVbdiRequest();
        request.setDocumentId(document.getId());
        request.setCoordinators(List.of(recipient("u1", "Tran Thi B", "nhanvien02")));

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        Map<String, Object> fields = fieldsOf(document);
        assertThat(String.valueOf(fields.get("coordinator_hide_not_seen_rtext"))).isEqualTo("nhanvien02");
        JsonNode obj = OBJECT_MAPPER.valueToTree(fields.get("xemdebiet_obj_rtext"));
        // Da co san trong danh sach cho -> KHONG them dong lich su moi (dedup theo memberKey).
        assertThat(obj).hasSize(1);
    }

    @Test
    void execute_whenObjSnapshotStoredAsNativeNestedArray_shouldStillMergeCorrectly() {
        // Bay du lieu "_obj"/"_rtext" da ghi nhan o vbden (vbden-obj-field-json-vs-string-trap): field
        // da tung duoc ghi qua tang suy luan kieu se tro thanh List/Map Java-native khi doc lai qua
        // parseDynamicFields, KHONG con la chuoi JSON tho.
        var document = vbdiDocument();
        document.replaceDynamicFields(dynamicFieldsWithNativeObjArray());
        when(vbdiDocumentRepository.findById(document.getId())).thenReturn(Optional.of(document));

        var request = new ChuyenXemDeBietVbdiRequest();
        request.setDocumentId(document.getId());
        request.setCoordinators(List.of(recipient("u2", "Le Van C", "nhanvien03")));

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        Map<String, Object> fields = fieldsOf(document);
        JsonNode obj = OBJECT_MAPPER.valueToTree(fields.get("xemdebiet_obj_rtext"));
        assertThat(obj.isArray()).isTrue();
        assertThat(obj).hasSize(2);
    }

    // "xemdebiet_obj_rtext" la GIA TRI MANG (khong bo trong dau nhay) o cap dynamic_fields JSON -
    // parseDynamicFields se tra ve List/Map Java-native khi doc lai (khac chuoi JSON tho o test tren).
    private String dynamicFieldsWithNativeObjArray() {
        return "{\"xemdebiet_obj_rtext\":[{\"username\":\"nhanvien02\",\"name\":\"Tran Thi B\"}]}";
    }
}
