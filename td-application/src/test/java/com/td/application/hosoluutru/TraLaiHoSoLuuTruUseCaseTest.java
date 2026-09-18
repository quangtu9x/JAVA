package com.td.application.hosoluutru;

import com.td.application.common.models.Result;
import com.td.application.common.security.CurrentUserProvider;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.application.hosocv.AddHoSoCVProcessDocumentUseCase;
import com.td.application.hosocv.HoSoCVDocumentRepository;
import com.td.application.sharedcore.AppUserRepository;
import com.td.domain.hosocv.HoSoCVDocument;
import com.td.domain.hosoluutru.HoSoLuuTruDocument;
import com.td.domain.hosoluutru.HoSoLuuTruVanBanDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Port module "Hồ sơ lưu trữ": {@link TraLaiHoSoLuuTruUseCase} — bước 2/3 (nhánh từ chối) của cơ chế
 * đồng bộ, xoá mềm ĐỆ QUY văn bản con (đúng hành vi {@code Xoa()} thật, tài liệu 16 mục 5.3/5.4).
 */
@ExtendWith(MockitoExtension.class)
class TraLaiHoSoLuuTruUseCaseTest {

    @Mock
    private HoSoLuuTruDocumentRepository hoSoLuuTruDocumentRepository;
    @Mock
    private HoSoLuuTruVanBanDocumentRepository hoSoLuuTruVanBanDocumentRepository;
    @Mock
    private HoSoCVDocumentRepository hoSoCVDocumentRepository;
    @Mock
    private CurrentUserProvider currentUserProvider;
    @Mock
    private AppUserRepository appUserRepository;
    @Mock
    private AddHoSoCVProcessDocumentUseCase addHoSoCVProcessDocumentUseCase;

    private final DynamicDocumentFieldsService dynamicDocumentFieldsService = new DynamicDocumentFieldsService();
    private TraLaiHoSoLuuTruUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new TraLaiHoSoLuuTruUseCase(
            hoSoLuuTruDocumentRepository, hoSoLuuTruVanBanDocumentRepository, hoSoCVDocumentRepository,
            dynamicDocumentFieldsService, currentUserProvider, appUserRepository, addHoSoCVProcessDocumentUseCase);
        lenient().when(currentUserProvider.currentUsername()).thenReturn(Optional.of("kholuutru01"));
        lenient().when(appUserRepository.findByUsernameAndDeletedOnIsNull(any())).thenReturn(Optional.empty());
        lenient().when(hoSoLuuTruDocumentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(hoSoLuuTruVanBanDocumentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(hoSoCVDocumentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(addHoSoCVProcessDocumentUseCase.execute(any())).thenReturn(Result.success(UUID.randomUUID()));
        lenient().when(hoSoLuuTruVanBanDocumentRepository.findByHosoId(any())).thenReturn(List.of());
    }

    @Test
    void execute_whenNotChoTiepNhan_shouldReturnFailure() {
        HoSoLuuTruDocument document = newDocument(UUID.randomUUID(), "hoso", "{}");
        when(hoSoLuuTruDocumentRepository.findById(document.getId())).thenReturn(Optional.of(document));

        assertThat(useCase.execute(request(document.getId())).isSuccess()).isFalse();
    }

    @Test
    void execute_whenChoTiepNhan_shouldSoftDeleteAndSyncStoraged3OnHoSoCV() {
        UUID hosocvId = UUID.randomUUID();
        HoSoLuuTruDocument document = newDocument(
            UUID.randomUUID(), "hoso_chuaduyet", "{\"document_profiles_id\":\"" + hosocvId + "\"}");
        when(hoSoLuuTruDocumentRepository.findById(document.getId())).thenReturn(Optional.of(document));
        HoSoCVDocument hosocv = newHosoCV(hosocvId, "{}");
        when(hoSoCVDocumentRepository.findById(hosocvId)).thenReturn(Optional.of(hosocv));

        var result = useCase.execute(request(document.getId()));

        assertThat(result.isSuccess()).isTrue();
        assertThat(document.isDeleted()).isTrue();
        Map<String, Object> hosocvFields = dynamicDocumentFieldsService.parseDynamicFields(hosocv.getDynamicFieldsJson());
        assertThat(hosocvFields.get("storaged")).isEqualTo("3");
    }

    @Test
    void execute_withVanBanChildren_shouldSoftDeleteThemToo() {
        HoSoLuuTruDocument document = newDocument(UUID.randomUUID(), "hoso_chuaduyet", "{}");
        when(hoSoLuuTruDocumentRepository.findById(document.getId())).thenReturn(Optional.of(document));
        HoSoLuuTruVanBanDocument vanBan = newVanBan(UUID.randomUUID());
        when(hoSoLuuTruVanBanDocumentRepository.findByHosoId(document.getId())).thenReturn(List.of(vanBan));

        useCase.execute(request(document.getId()));

        assertThat(vanBan.isDeleted()).isTrue();
    }

    private TiepNhanHoSoLuuTruRequest request(UUID id) {
        var request = new TiepNhanHoSoLuuTruRequest();
        request.setHosoLuuTruId(id);
        return request;
    }

    private HoSoLuuTruDocument newDocument(UUID id, String status, String dynamicFieldsJson) {
        HoSoLuuTruDocument document = new HoSoLuuTruDocument("Ho so luu tru test", "hosoluutru", status, null, null, null, null);
        document.replaceDynamicFields(dynamicFieldsJson);
        ReflectionTestUtils.setField(document, "id", id);
        return document;
    }

    private HoSoLuuTruVanBanDocument newVanBan(UUID id) {
        HoSoLuuTruVanBanDocument document = new HoSoLuuTruVanBanDocument("Van ban test", "vanban", null, null, null, null, null);
        ReflectionTestUtils.setField(document, "id", id);
        return document;
    }

    private HoSoCVDocument newHosoCV(UUID id, String dynamicFieldsJson) {
        HoSoCVDocument document = new HoSoCVDocument("Ho so cong viec test", "hosocv", "1", null, null, null, null);
        document.replaceDynamicFields(dynamicFieldsJson);
        ReflectionTestUtils.setField(document, "id", id);
        return document;
    }
}
