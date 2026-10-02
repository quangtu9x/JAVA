package com.td.infrastructure.onlyoffice;

import com.td.application.common.security.CurrentUserProvider;
import com.td.application.onlyoffice.OnlyOfficeEditorConfigRequest;
import com.td.infrastructure.persistence.entity.FileMetadataEntity;
import com.td.infrastructure.persistence.repository.FileMetadataJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 30/09/2026 (yêu cầu người dùng: "Đọc tệp đính kèm" mở Word/Excel bằng OnlyOffice CHỈ XEM) — chế độ
 * {@code viewOnly} không được cấp callback lưu, không cho sửa, dùng key riêng; mặc định giữ nguyên chế độ
 * sửa.
 */
class BuildOnlyOfficeEditorConfigUseCaseImplTest {

    private final FileMetadataJpaRepository fileMetadataRepository = mock(FileMetadataJpaRepository.class);
    private final OnlyOfficeAccessTokenService accessTokenService = mock(OnlyOfficeAccessTokenService.class);
    private final OnlyOfficeDocumentJwtService documentJwtService = mock(OnlyOfficeDocumentJwtService.class);
    private final CurrentUserProvider currentUserProvider = mock(CurrentUserProvider.class);
    private BuildOnlyOfficeEditorConfigUseCaseImpl useCase;
    private final UUID fileId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        OnlyOfficeProperties properties = new OnlyOfficeProperties();
        properties.setDocserverUrl("http://docserver");
        properties.setCallbackBaseUrl("http://backend");
        useCase = new BuildOnlyOfficeEditorConfigUseCaseImpl(
                fileMetadataRepository, properties, accessTokenService, documentJwtService, currentUserProvider);

        FileMetadataEntity file = FileMetadataEntity.builder()
                .id(fileId).documentId(UUID.randomUUID()).originalFilename("baocao.docx")
                .uploadedAt(LocalDateTime.of(2026, 9, 30, 8, 0)).build();
        when(fileMetadataRepository.findById(fileId)).thenReturn(Optional.of(file));
        when(currentUserProvider.currentUsername()).thenReturn(Optional.of("nguyenvana"));
        when(accessTokenService.issue(anyString(), any(), any(), any(), anyString(), any())).thenReturn("tok");
    }

    @SuppressWarnings("unchecked")
    @Test
    void viewOnly_shouldUseViewModeWithoutCallbackAndEdit() {
        var result = useCase.execute(OnlyOfficeEditorConfigRequest.builder().fileId(fileId).viewOnly(true).build());

        assertThat(result.isSuccess()).isTrue();
        Map<String, Object> editorConfig = (Map<String, Object>) result.getData().get("editorConfig");
        Map<String, Object> document = (Map<String, Object>) result.getData().get("document");
        assertThat(editorConfig.get("mode")).isEqualTo("view");
        assertThat(editorConfig).doesNotContainKey("callbackUrl");
        assertThat(((Map<String, Object>) document.get("permissions")).get("edit")).isEqualTo(false);
        assertThat((String) document.get("key")).startsWith("oov_");
        Map<String, Object> customization = (Map<String, Object>) editorConfig.get("customization");
        assertThat((Map<String, Object>) customization.get("layout"))
                .containsEntry("toolbar", false).containsEntry("leftMenu", false);
        assertThat(customization.get("plugins")).isEqualTo(false);
        assertThat(((Map<String, Object>) document.get("permissions")).get("comment")).isEqualTo(false);
        verify(accessTokenService, never()).issue(eq(OnlyOfficeAccessTokenService.PURPOSE_CALLBACK),
                any(), any(), any(), anyString(), any());
    }

    @SuppressWarnings("unchecked")
    @Test
    void default_shouldKeepEditModeWithCallback() {
        var result = useCase.execute(OnlyOfficeEditorConfigRequest.builder().fileId(fileId).build());

        Map<String, Object> editorConfig = (Map<String, Object>) result.getData().get("editorConfig");
        Map<String, Object> document = (Map<String, Object>) result.getData().get("document");
        assertThat(editorConfig.get("mode")).isEqualTo("edit");
        assertThat((String) editorConfig.get("callbackUrl")).startsWith("http://backend/api/v1/onlyoffice/callback");
        assertThat(((Map<String, Object>) document.get("permissions")).get("edit")).isEqualTo(true);
        assertThat((String) document.get("key")).startsWith("oo_");
        assertThat((Map<String, Object>) editorConfig.get("customization")).doesNotContainKey("layout");
    }
}
