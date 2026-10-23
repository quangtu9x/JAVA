package com.td.application.tonghop;

import com.td.application.common.models.Result;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static com.td.application.tonghop.ProvinceScopeTestSupport.scope;
import static com.td.application.tonghop.ProvinceScopeTestSupport.unitAdmin;
import static org.assertj.core.api.Assertions.assertThat;

/** Phạm vi các màn /tonghop (người dùng chốt 22/10/2026 — tdgateway/docs/24 §2.8). */
class ProvinceScopePolicyTest {

    // ------------------------------------------------------------------ ProvinceScopePolicy

    @Test
    void superAccountIsProvinceFromAnyTenant() {
        ProvinceScope s = scope("admin", "H05_01", null).current();
        assertThat(s.isResolved()).isTrue();
        assertThat(s.isSuperAccount()).isTrue();
        assertThat(s.restrictTenantId()).isNull();
    }

    @Test
    void platformAdminIsSuperAccount() {
        assertThat(scope("root", "H05_02", null, "PLATFORM_ADMIN").current().isSuperAccount()).isTrue();
    }

    @Test
    void provinceTenantAdminIsProvinceButNotSuperAccount() {
        ProvinceScope s = scope("qtv.tinh", "H05", "H05", "ADMIN").current();
        assertThat(s.isProvince()).isTrue();
        assertThat(s.isSuperAccount()).isFalse();
    }

    @Test
    void unitAdminIsRestrictedToOwnTenant() {
        ProvinceScope s = unitAdmin("H05_01").current();
        assertThat(s.isProvince()).isFalse();
        assertThat(s.restrictTenantId()).isEqualTo("H05_01");
        assertThat(s.covers("H05_01")).isTrue();
        assertThat(s.covers("H05_02")).isFalse();
    }

    @Test
    void defaultTenantFallbackIsUnresolved() {
        // Hồ sơ không suy được tenant → TenantResolver đã dùng mặc định H05: KHÔNG được coi là tỉnh.
        assertThat(scope("qtv.moi", "H05", null, "ADMIN").current().isResolved()).isFalse();
    }

    @Test
    void tokenTenantMismatchIsUnitScope() {
        ProvinceScope s = scope("qtv", "H05", "H05_01", "ADMIN").current();
        assertThat(s.isResolved()).isTrue();
        assertThat(s.isProvince()).isFalse();
        assertThat(s.restrictTenantId()).isEqualTo("H05");
    }

    @Test
    void unresolvedCoversNothing() {
        assertThat(ProvinceScope.unresolved().covers("H05")).isFalse();
    }

    // ------------------------------------------------------------------ Use case /tonghop cũ

    @Test
    void documentSearchIsForcedToOwnTenantEvenIfClientTriesOther() {
        var repo = new ProvinceScopeTestSupport.CapturingRepository();
        ProvinceDocumentSearchRequest request = new ProvinceDocumentSearchRequest();
        request.setScopeTenantId("H05_02"); // client (giả sử lách được @JsonIgnore) — phải bị ghi đè
        new SearchProvinceDocumentsUseCase(repo, unitAdmin("H05_01")).execute(request);
        assertThat(repo.documentRequest.getScopeTenantId()).isEqualTo("H05_01");
    }

    @Test
    void documentSearchForProvinceAdminHasNoTenantRestriction() {
        var repo = new ProvinceScopeTestSupport.CapturingRepository();
        ProvinceDocumentSearchRequest request = new ProvinceDocumentSearchRequest();
        request.setScopeTenantId("H05_02");
        new SearchProvinceDocumentsUseCase(repo, scope("admin", "H05", null)).execute(request);
        assertThat(repo.documentRequest.getScopeTenantId()).isNull();
    }

    @Test
    void unresolvedUserCannotSearch() {
        Result<ProvinceDocumentSearchResult> result = new SearchProvinceDocumentsUseCase(
            new ProvinceScopeTestSupport.CapturingRepository(), scope("qtv.moi", "H05", null, "ADMIN"))
            .execute(new ProvinceDocumentSearchRequest());
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getError()).isEqualTo(SearchProvinceDocumentsUseCase.SCOPE_UNRESOLVED);
    }

    @Test
    void dispatchListAndSummaryAreScoped() {
        var repo = new ProvinceScopeTestSupport.CapturingRepository();
        var useCase = new SearchProvinceDispatchesUseCase(repo, unitAdmin("H05_01"));
        useCase.execute(new ProvinceDispatchSearchRequest());
        assertThat(repo.dispatchRequest.getScopeTenantId()).isEqualTo("H05_01");
        repo.dispatchRequest = null;
        useCase.summary(new ProvinceDispatchSearchRequest());
        assertThat(repo.dispatchRequest.getScopeTenantId()).isEqualTo("H05_01");
    }

    @Test
    void dispatchDetailOfOtherTenantIsNotFound() {
        var useCase = new GetProvinceDispatchDetailUseCase(new ProvinceScopeTestSupport.CapturingRepository(),
            unitAdmin("H05_01"));
        assertThat(useCase.execute("H05_02", UUID.randomUUID()).isSuccess()).isFalse();
        assertThat(useCase.execute("H05_01", UUID.randomUUID()).isSuccess()).isTrue();
    }

    @Test
    void resendOfOtherTenantIsRejectedBeforeTouchingAnything() {
        var repo = new ProvinceScopeTestSupport.CapturingRepository();
        // Các phụ thuộc gửi thật để null: nếu bị gọi tới sẽ NPE → test bắt được.
        var useCase = new ResendProvinceDispatchUseCase(repo, null, null, null, unitAdmin("H05_01"));
        ResendProvinceDispatchRequest request = new ResendProvinceDispatchRequest();
        request.setTenantId("H05_02");
        request.setDocumentId(UUID.randomUUID());
        request.setRecipientKeys(List.of("k"));
        Result<Integer> result = useCase.execute(request);
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getError()).isEqualTo("Bạn chỉ được gửi lại văn bản của đơn vị mình");
        assertThat(repo.recipientsLoaded).isFalse();
    }
}
