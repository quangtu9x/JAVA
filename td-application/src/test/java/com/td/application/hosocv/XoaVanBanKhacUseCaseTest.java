package com.td.application.hosocv;

import com.td.application.common.security.CurrentUserProvider;
import com.td.application.documents.DocumentCacheService;
import com.td.application.documents.DocumentLogService;
import com.td.application.documents.DynamicDocumentFieldsService;
import com.td.application.sharedcore.AppUserRepository;
import com.td.domain.hosocv.HoSoCVDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 02/10/2026 — {@link XoaVanBanKhacUseCase}: xoá mềm, không xoá nhầm hồ sơ công việc. */
@ExtendWith(MockitoExtension.class)
class XoaVanBanKhacUseCaseTest {

    @Mock
    private HoSoCVDocumentRepository hoSoCVDocumentRepository;
    @Mock
    private DocumentCacheService documentCacheService;
    @Mock
    private DocumentLogService documentLogService;
    @Mock
    private CurrentUserProvider currentUserProvider;
    @Mock
    private AppUserRepository appUserRepository;

    private XoaVanBanKhacUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new XoaVanBanKhacUseCase(hoSoCVDocumentRepository, new DynamicDocumentFieldsService(),
            documentCacheService, documentLogService, currentUserProvider, appUserRepository);
    }

    @Test
    void execute_shouldSoftDeleteVanBanKhac() {
        HoSoCVDocument vanBanKhac = new HoSoCVDocument("VB", LuuVanBanKhacUseCase.DOCUMENT_TYPE, "1", null, null, null, null);
        when(hoSoCVDocumentRepository.findById(vanBanKhac.getId())).thenReturn(Optional.of(vanBanKhac));
        when(hoSoCVDocumentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.empty());

        var result = useCase.execute(List.of(vanBanKhac.getId()));

        assertThat(result.isSuccess()).isTrue();
        assertThat(vanBanKhac.isDeleted()).isTrue();
    }

    @Test
    void execute_withHoSoCongViecId_shouldNotDelete() {
        HoSoCVDocument hosocv = new HoSoCVDocument("Hồ sơ", "hosocv", "0", null, null, null, null);
        when(hoSoCVDocumentRepository.findById(hosocv.getId())).thenReturn(Optional.of(hosocv));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.empty());

        var result = useCase.execute(List.of(hosocv.getId()));

        assertThat(result.isSuccess()).isFalse();
        assertThat(hosocv.isDeleted()).isFalse();
        verify(hoSoCVDocumentRepository, never()).save(any());
    }
}
