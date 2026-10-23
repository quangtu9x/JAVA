package com.td.web.controllers.v1;

import com.td.application.common.models.ApiResponse;
import com.td.application.common.models.Result;
import com.td.application.tonghop.national.CancelNationalExchangeOperationUseCase;
import com.td.application.tonghop.national.DownloadNationalExchangePackageUseCase;
import com.td.application.tonghop.national.GetNationalExchangeAccessUseCase;
import com.td.application.tonghop.national.GetNationalExchangeDetailUseCase;
import com.td.application.tonghop.national.NationalExchangeAccess;
import com.td.application.tonghop.national.NationalExchangeDetail;
import com.td.application.tonghop.national.NationalExchangeOperationRequest;
import com.td.application.tonghop.national.NationalExchangePackageGateway;
import com.td.application.tonghop.national.NationalExchangeSearchRequest;
import com.td.application.tonghop.national.NationalExchangeSearchResult;
import com.td.application.tonghop.national.NationalExchangeSummary;
import com.td.application.tonghop.national.RequestNationalExchangeOperationUseCase;
import com.td.application.tonghop.national.SearchNationalExchangesUseCase;
import com.td.web.controllers.BaseController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Giám sát gửi nhận văn bản qua Trục liên thông văn bản quốc gia (route FE {@code /tonghop/trucquocgia}) — đọc
 * read model quản trị trong DB riêng của TD Gateway (tdgateway/docs/22, 23). Tách khỏi {@link TongHopController}
 * để không đổi constructor controller đang chạy.
 *
 * <p>22/10/2026 (docs/24 §4.3): thêm thao tác quản trị — chỉ GHI lệnh vào hàng đợi của gateway (202), bộ thực thi
 * gateway chạy bất đồng bộ; bật bằng {@code NATIONAL_EXCHANGE_OPERATIONS_ENABLED}. Tải gói = proxy endpoint nội bộ
 * của gateway ({@code NATIONAL_EXCHANGE_GATEWAY_URL}).</p>
 *
 * <p>Quyền (tdgateway/docs/24 §4.2): cổng thô ở đây = {@code ADMIN}/{@code PLATFORM_ADMIN}/mã quyền
 * {@code NATIONAL_EXCHANGE_VIEW} (403 nếu không có); phạm vi tenant + che dữ liệu kỹ thuật (thiếu
 * {@code NATIONAL_EXCHANGE_AUDIT}) do {@code NationalExchangeAccessPolicy} áp trong use case.</p>
 */
@RestController
@RequestMapping("/api/v1/tonghop/TrucQuocGia")
@RequiredArgsConstructor
@Tag(name = "TongHop - TrucQuocGia", description = "Giám sát gửi nhận văn bản qua Trục quốc gia (TD Gateway)")
public class TrucQuocGiaController extends BaseController {

    static final String CAN_VIEW = "hasAnyRole('ADMIN','PLATFORM_ADMIN','NATIONAL_EXCHANGE_VIEW')";

    private final SearchNationalExchangesUseCase searchNationalExchangesUseCase;
    private final GetNationalExchangeDetailUseCase getNationalExchangeDetailUseCase;
    private final GetNationalExchangeAccessUseCase getNationalExchangeAccessUseCase;
    private final RequestNationalExchangeOperationUseCase requestNationalExchangeOperationUseCase;
    private final CancelNationalExchangeOperationUseCase cancelNationalExchangeOperationUseCase;
    private final DownloadNationalExchangePackageUseCase downloadNationalExchangePackageUseCase;

    @GetMapping("/Quyen")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Quyền + phạm vi (toàn tỉnh/đơn vị) của người dùng hiện tại trên màn Trục quốc gia")
    public ResponseEntity<ApiResponse<NationalExchangeAccess>> quyen() {
        return respond(getNationalExchangeAccessUseCase.execute());
    }

    @PostMapping
    @PreAuthorize(CAN_VIEW)
    @Operation(summary = "Danh sách gói gửi/nhận qua Trục quốc gia (lọc chiều, loại, trạng thái, đơn vị, thời gian)")
    public ResponseEntity<ApiResponse<NationalExchangeSearchResult>> danhSach(
            @RequestBody(required = false) NationalExchangeSearchRequest request) {
        return respond(searchNationalExchangesUseCase.execute(request));
    }

    @PostMapping("/ThongKe")
    @PreAuthorize(CAN_VIEW)
    @Operation(summary = "Thẻ số liệu + số gói theo ngày (cùng bộ lọc danh sách, bỏ lọc trạng thái)")
    public ResponseEntity<ApiResponse<NationalExchangeSummary>> thongKe(
            @RequestBody(required = false) NationalExchangeSearchRequest request) {
        return respond(searchNationalExchangesUseCase.summary(request));
    }

    @GetMapping("/ChiTiet")
    @PreAuthorize(CAN_VIEW)
    @Operation(summary = "Chi tiết 1 gói: nơi nhận, dòng thời gian, từng lần thao tác Trục, yêu cầu quản trị")
    public ResponseEntity<ApiResponse<NationalExchangeDetail>> chiTiet(@RequestParam String exchangeId) {
        return respond(getNationalExchangeDetailUseCase.execute(exchangeId));
    }

    /**
     * Tạo lệnh quản trị (gửi lại/nhận lại/phát lại/xử lý lại) — ghi hàng đợi của gateway, trả {@code 202} ngay; bộ
     * thực thi gateway chạy bất đồng bộ (docs/22 §4). Header {@code Idempotency-Key} chống bấm/gửi trùng.
     */
    @PostMapping("/ThaoTac")
    @PreAuthorize(CAN_VIEW)
    @Operation(summary = "Tạo yêu cầu thao tác quản trị trên 1 gói (202 + mã yêu cầu; gateway thực thi bất đồng bộ)")
    public ResponseEntity<ApiResponse<NationalExchangeDetail.Operation>> thaoTac(
            @RequestParam String exchangeId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestParam(value = "idempotencyKey", required = false) String idempotencyKeyParam,
            @RequestBody NationalExchangeOperationRequest request) {
        // FE (baseAPI.requestPOST) không gắn header tuỳ ý → nhận cả tham số URL; header ưu tiên.
        String key = idempotencyKey != null && !idempotencyKey.isBlank() ? idempotencyKey : idempotencyKeyParam;
        Result<NationalExchangeDetail.Operation> result =
            requestNationalExchangeOperationUseCase.execute(exchangeId, request, key);
        return result.isSuccess()
            ? ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.success(result.getData()))
            : badRequest(result);
    }

    @PostMapping("/ThaoTac/Huy")
    @PreAuthorize(CAN_VIEW)
    @Operation(summary = "Huỷ yêu cầu thao tác còn đang chờ (PENDING)")
    public ResponseEntity<ApiResponse<NationalExchangeDetail.Operation>> huyThaoTac(@RequestParam String requestId) {
        return respond(cancelNationalExchangeOperationUseCase.execute(requestId));
    }

    /** Tải gói eDXML (quyền NATIONAL_EXCHANGE_DOWNLOAD) — proxy stream từ gateway, gateway ghi audit người tải. */
    @GetMapping("/TaiGoi")
    @PreAuthorize(CAN_VIEW)
    @Operation(summary = "Tải tệp gói eDXML đã lưu trên TD Gateway")
    public ResponseEntity<?> taiGoi(@RequestParam String exchangeId) {
        Result<NationalExchangePackageGateway.PackageStream> result =
            downloadNationalExchangePackageUseCase.execute(exchangeId);
        if (!result.isSuccess()) {
            return badRequest(result);
        }
        NationalExchangePackageGateway.PackageStream pkg = result.getData();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(ContentDisposition.attachment().filename(pkg.fileName()).build());
        if (pkg.contentSha256() != null) {
            headers.add("X-Content-Sha256", pkg.contentSha256());
        }
        if (pkg.contentLength() != null) {
            headers.setContentLength(pkg.contentLength());
        }
        return ResponseEntity.ok().headers(headers).contentType(MediaType.APPLICATION_XML)
            .body(new InputStreamResource(pkg.content()));
    }

    private <T> ResponseEntity<ApiResponse<T>> respond(Result<T> result) {
        return result.isSuccess() ? ok(result) : badRequest(result);
    }
}
