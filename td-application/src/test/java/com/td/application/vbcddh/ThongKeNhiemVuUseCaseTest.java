package com.td.application.vbcddh;

import com.td.domain.vbcddh.VbcddhDocument;

import com.td.application.documents.DynamicDocumentFieldsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * BL-250 (VBCDDH Core) — DoD (4): thống kê nhiệm vụ qua {@link ThongKeNhiemVuUseCase}, đúng 6 tổ
 * hợp cố định (tinhtrang × quahan) như {@code ThongKeNhiemVu.java} thật.
 */
@ExtendWith(MockitoExtension.class)
class ThongKeNhiemVuUseCaseTest {

    @Mock
    private VbcddhDocumentRepository vbcddhDocumentRepository;

    private final DynamicDocumentFieldsService dynamicDocumentFieldsService = new DynamicDocumentFieldsService();
    private ThongKeNhiemVuUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new ThongKeNhiemVuUseCase(vbcddhDocumentRepository, dynamicDocumentFieldsService);
    }

    @Test
    void execute_withoutIdentifiersOrNguoi_shouldReturnFailure() {
        var result = useCase.execute(new ThongKeNhiemVuRequest());

        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    void execute_shouldBucketByTinhTrangAndQuaHan() {
        VbcddhDocument chua = newNhiemVu("Chưa thực hiện", "0", null);
        VbcddhDocument chuaQh = newNhiemVu("Chưa thực hiện", "1", null);
        VbcddhDocument dang = newNhiemVu("Đang thực hiện", "0", null);
        VbcddhDocument da = newNhiemVu("Đã thực hiện", "0", null);
        VbcddhDocument daQh = newNhiemVu("Đã thực hiện", "1", null);
        stub("donvi_a", List.of(chua, chuaQh, dang, da, daQh));

        var request = new ThongKeNhiemVuRequest();
        request.setIdentifiers(List.of("donvi_a"));

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        var buckets = result.getData().get("donvi_a");
        assertThat(buckets).hasSize(6);
        assertThat(count(buckets, "Chưa thực hiện")).isEqualTo(1);
        assertThat(count(buckets, "Chưa thực hiện (quá hạn)")).isEqualTo(1);
        assertThat(count(buckets, "Đang thực hiện")).isEqualTo(1);
        assertThat(count(buckets, "Đã thực hiện")).isEqualTo(1);
        assertThat(count(buckets, "Đã thực hiện (quá hạn)")).isEqualTo(1);
    }

    @Test
    void execute_shouldFilterByCreateDateRange() {
        VbcddhDocument insideRange = newNhiemVu("Đã thực hiện", "0", "2026-08-05");
        VbcddhDocument outsideRange = newNhiemVu("Đã thực hiện", "0", "2026-01-01");
        stub("donvi_a", List.of(insideRange, outsideRange));

        var request = new ThongKeNhiemVuRequest();
        request.setIdentifiers(List.of("donvi_a"));
        request.setTuNgay("2026-08-01");
        request.setDenNgay("2026-08-10");

        var result = useCase.execute(request);

        var buckets = result.getData().get("donvi_a");
        assertThat(count(buckets, "Đã thực hiện")).isEqualTo(1);
    }

    @Test
    void execute_withNguoiList_shouldQueryByNguoiFieldInsteadOfIdentifier() {
        VbcddhDocument document = newNhiemVu("Chưa thực hiện", "0", null);
        Page<VbcddhDocument> page = new PageImpl<>(List.of(document));
        when(vbcddhDocumentRepository.findByFieldValue(eq("vbcddh"), eq("chuyenvienthuchienhide"), eq("nguoi_a"), any()))
            .thenReturn(page);

        var request = new ThongKeNhiemVuRequest();
        request.setNguoiList(List.of("nguoi_a"));

        var result = useCase.execute(request);

        assertThat(result.isSuccess()).isTrue();
        assertThat(count(result.getData().get("nguoi_a"), "Chưa thực hiện")).isEqualTo(1);
    }

    private long count(List<NhiemVuThongKeItemDto> buckets, String label) {
        return buckets.stream().filter(item -> item.getLabel().equals(label)).mapToLong(NhiemVuThongKeItemDto::getCount).sum();
    }

    private void stub(String identifier, List<VbcddhDocument> documents) {
        Page<VbcddhDocument> page = new PageImpl<>(documents);
        when(vbcddhDocumentRepository.findByFieldValue(eq("vbcddh"), eq("donvithuchienhide"), eq(identifier), any()))
            .thenReturn(page);
    }

    private VbcddhDocument newNhiemVu(String tinhtrang, String quahan, String createDate) {
        StringBuilder json = new StringBuilder("{\"form\":\"nhiemvu\",\"tinhtrang\":\"" + tinhtrang + "\",\"quahan\":\"" + quahan + "\"");
        if (createDate != null) {
            json.append(",\"create_date\":\"").append(createDate).append("\"");
        }
        json.append("}");
        VbcddhDocument document = new VbcddhDocument("Nhiem vu", "vbcddh", "1", null, null, null, null);
        document.replaceDynamicFields(json.toString());
        return document;
    }
}
