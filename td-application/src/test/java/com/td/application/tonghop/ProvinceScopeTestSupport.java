package com.td.application.tonghop;

import com.td.application.common.security.CurrentTenantProvider;
import com.td.application.common.security.CurrentUserProvider;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Người dùng/tenant giả + kho tổng hợp giả cho test phạm vi — không cần Mockito. */
final class ProvinceScopeTestSupport {

    private ProvinceScopeTestSupport() {
    }

    /** {@code profileTenant} = tenant suy từ hồ sơ AppUser (null = không suy được). */
    static ProvinceScopePolicy scope(String username, String tenantId, String profileTenant, String... roles) {
        CurrentUserProvider user = new CurrentUserProvider() {
            @Override
            public Optional<String> currentUsername() {
                return Optional.ofNullable(username);
            }

            @Override
            public Set<String> currentRoles() {
                return Set.of(roles);
            }
        };
        CurrentTenantProvider tenant = () -> Optional.ofNullable(tenantId);
        return new ProvinceScopePolicy(user, tenant, u -> Optional.ofNullable(profileTenant), "H05");
    }

    static ProvinceScopePolicy unitAdmin(String tenantId) {
        return scope("qtv", tenantId, tenantId, "ADMIN");
    }

    /** Ghi lại request mà use case truyền xuống repository. */
    static class CapturingRepository implements ProvinceQueryRepository {
        ProvinceDocumentSearchRequest documentRequest;
        ProvinceDispatchSearchRequest dispatchRequest;
        boolean recipientsLoaded;

        @Override
        public ProvinceDocumentSearchResult searchDocuments(ProvinceDocumentSearchRequest request, LocalDate from,
                                                            LocalDate to, int offset, int limit) {
            documentRequest = request;
            return ProvinceDocumentSearchResult.builder().items(List.of()).build();
        }

        @Override
        public ProvinceStatisticsResult statistics(LocalDate from, LocalDate to) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ProvinceDispatchPage searchDispatchDocuments(ProvinceDispatchSearchRequest request, LocalDate from,
                                                            LocalDate to, int lateArrivalMinutes, int lateAcceptHours,
                                                            int offset, int limit) {
            dispatchRequest = request;
            return new ProvinceDispatchPage(List.of(), 0);
        }

        @Override
        public ProvinceDispatchSummary dispatchSummary(ProvinceDispatchSearchRequest request, LocalDate from,
                                                       LocalDate to, int lateArrivalMinutes, int lateAcceptHours) {
            dispatchRequest = request;
            return ProvinceDispatchSummary.builder().build();
        }

        @Override
        public List<ProvinceDispatchRecipientItem> dispatchRecipients(String tenantId, UUID vbdiId) {
            recipientsLoaded = true;
            return List.of();
        }

        @Override
        public Optional<ProvinceDocumentItem> findDocument(String tenantId, UUID documentId) {
            return Optional.of(ProvinceDocumentItem.builder().tenantId(tenantId).documentId(documentId).build());
        }
    }
}
