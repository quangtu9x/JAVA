package com.td.application.phieutrinhtd;

import com.td.application.common.security.CurrentUserProvider;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.application.sharedcore.AppUserRepository;
import com.td.domain.phieutrinhtd.PhieuTrinhTdDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TrinhThamKhaoPhieuTrinhTdUseCaseTest {

    @Mock
    private PhieuTrinhTdDocumentRepository phieuTrinhTdDocumentRepository;
    @Mock
    private PhieuTrinhTdProcessDocumentRepository phieuTrinhTdProcessDocumentRepository;
    @Mock
    private CurrentUserProvider currentUserProvider;
    @Mock
    private AppUserRepository appUserRepository;
    @Mock
    private com.td.application.documents.AuthorAclFieldsService authorAclFieldsService;

    private final DynamicDocumentFieldsService dynamicDocumentFieldsService = new DynamicDocumentFieldsService();
    private TrinhThamKhaoPhieuTrinhTdUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new TrinhThamKhaoPhieuTrinhTdUseCase(
            phieuTrinhTdDocumentRepository, phieuTrinhTdProcessDocumentRepository,
            dynamicDocumentFieldsService, currentUserProvider, appUserRepository, authorAclFieldsService);
        lenient().when(phieuTrinhTdDocumentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(phieuTrinhTdProcessDocumentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(currentUserProvider.currentUsername()).thenReturn(Optional.of("nguyenvana"));
        lenient().when(appUserRepository.findByUsernameAndDeletedOnIsNull(any())).thenReturn(Optional.empty());
    }

    @Test
    void execute_withoutId_shouldReturnFailure() {
        assertThat(useCase.execute(new TrinhThamKhaoPhieuTrinhTdRequest()).isSuccess()).isFalse();
    }

    @Test
    void execute_shouldAppendNewMembersWithoutDuplicatingExisting() {
        UUID id = UUID.randomUUID();
        PhieuTrinhTdDocument document = new PhieuTrinhTdDocument(
            "Phieu trinh A", PhieuTrinhTdDocument.STATUS_DANG_SOAN_THAO, null);
        document.replaceDynamicFields("{\"nguoithamkhaohide\":\"hueltm\"}");
        ReflectionTestUtils.setField(document, "id", id);
        when(phieuTrinhTdDocumentRepository.findById(id)).thenReturn(Optional.of(document));

        var request = new TrinhThamKhaoPhieuTrinhTdRequest();
        request.setId(id);
        request.setNguoiThamKhao("Nguyen Van B");
        request.setNguoiThamKhaoHide("hueltm;nguyenvanb");

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        // KHONG doi status chinh - tai lieu 20 muc 2.3.
        assertThat(document.getStatus()).isEqualTo(PhieuTrinhTdDocument.STATUS_DANG_SOAN_THAO);
        Map<String, Object> fields = dynamicDocumentFieldsService.parseDynamicFields(document.getDynamicFieldsJson());
        assertThat((String) fields.get("nguoithamkhaohide")).contains("hueltm").contains("nguyenvanb");
        // khong duplicate "hueltm"
        assertThat(((String) fields.get("nguoithamkhaohide")).split(";")).containsExactlyInAnyOrder("hueltm", "nguyenvanb");
    }
}
