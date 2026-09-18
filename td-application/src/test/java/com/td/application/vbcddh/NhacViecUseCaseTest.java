package com.td.application.vbcddh;

import com.td.domain.vbcddh.VbcddhDocument;

import com.td.application.common.security.CurrentUserProvider;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.application.sharedcore.AppUserRepository;
import com.td.domain.sharedcore.AppUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * BL-250 (VBCDDH Core) — DoD (5): nhắc việc tự động qua {@link NhacViecUseCase}, đúng ngưỡng "sắp
 * đến hạn" thật (hanxuly_date trong [hôm nay, ngày mai] — {@code NhacViec.java}).
 */
@ExtendWith(MockitoExtension.class)
class NhacViecUseCaseTest {

    @Mock
    private VbcddhDocumentRepository vbcddhDocumentRepository;
    @Mock
    private CurrentUserProvider currentUserProvider;
    @Mock
    private AppUserRepository appUserRepository;

    private final DynamicDocumentFieldsService dynamicDocumentFieldsService = new DynamicDocumentFieldsService();
    private NhacViecUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new NhacViecUseCase(vbcddhDocumentRepository, dynamicDocumentFieldsService, currentUserProvider, appUserRepository);
    }

    @Test
    void execute_withoutResolvableUser_shouldReturnFailure() {
        when(currentUserProvider.currentUsername()).thenReturn(Optional.empty());

        var result = useCase.execute();

        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    void execute_shouldGroupTasksByStatus() {
        UUID orgId = UUID.randomUUID();
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("nguyenvana"));
        AppUser user = new AppUser("kc-sub", "nguyenvana", "Nguyen Van A", "a@example.com", orgId, null, true);
        when(appUserRepository.findByUsernameAndDeletedOnIsNull("nguyenvana")).thenReturn(Optional.of(user));

        VbcddhDocument chuaThucHien = newNhiemVu("{\"tinhtrang\":\"Chưa thực hiện\"}");
        VbcddhDocument daThucHien = newNhiemVu("{\"tinhtrang\":\"Đã thực hiện\",\"quahan\":\"1\"}");
        VbcddhDocument sapDenHan = newNhiemVu(
            "{\"tinhtrang\":\"Đang thực hiện\",\"hanxuly_date\":\"" + LocalDate.now().plusDays(1) + "\"}");

        Page<VbcddhDocument> page = new PageImpl<>(List.of(chuaThucHien, daThucHien, sapDenHan));
        when(vbcddhDocumentRepository.findByFieldValue(eq("vbcddh"), eq("donvithuchienhide"), eq(orgId.toString()), any()))
            .thenReturn(page);

        var result = useCase.execute();

        assertThat(result.isSuccess()).isTrue();
        var response = result.getData();
        assertThat(labelCount(response.getDangThucHien(), "Chưa hoàn thành")).isEqualTo(1);
        assertThat(labelCount(response.getDangThucHien(), "Sắp đến hạn")).isEqualTo(1);
        assertThat(labelCount(response.getDaHoanThanh(), "Quá hạn")).isEqualTo(1);
        assertThat(labelCount(response.getDaHoanThanh(), "Đúng hạn")).isEqualTo(0);
    }

    private long labelCount(List<NhacViecItemDto> items, String label) {
        return items.stream().filter(item -> item.getLabel().equals(label)).mapToLong(NhacViecItemDto::getCount).sum();
    }

    private VbcddhDocument newNhiemVu(String fieldsWithoutForm) {
        String json = fieldsWithoutForm.substring(0, fieldsWithoutForm.length() - 1) + ",\"form\":\"nhiemvu\"}";
        VbcddhDocument document = new VbcddhDocument("Nhiem vu", "vbcddh", "1", null, null, null, null);
        document.replaceDynamicFields(json);
        return document;
    }
}
