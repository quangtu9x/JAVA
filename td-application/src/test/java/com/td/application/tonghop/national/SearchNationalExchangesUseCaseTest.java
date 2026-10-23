package com.td.application.tonghop.national;

import com.td.application.common.models.Result;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class SearchNationalExchangesUseCaseTest {

    /** Repository giả ghi lại tham số đã nhận. */
    static class FakeRepository implements NationalExchangeQueryRepository {
        LocalDate from;
        LocalDate to;
        int offset;
        int limit;
        boolean fail;
        NationalExchangeSearchRequest request;
        final List<NationalExchangeItem> items = new ArrayList<>();

        @Override
        public NationalExchangeSearchResult search(NationalExchangeSearchRequest request, LocalDate from, LocalDate to,
                                                   int offset, int limit) {
            if (fail) {
                throw new IllegalStateException("connection refused");
            }
            this.request = request;
            this.from = from;
            this.to = to;
            this.offset = offset;
            this.limit = limit;
            return NationalExchangeSearchResult.builder().items(items).totalItems(items.size()).build();
        }

        @Override
        public NationalExchangeSummary summary(NationalExchangeSearchRequest request, LocalDate from, LocalDate to) {
            return NationalExchangeSummary.builder().build();
        }

        @Override
        public Optional<NationalExchangeDetail> findDetail(String exchangeId) {
            return Optional.empty();
        }
    }

    private static SearchNationalExchangesUseCase useCase(FakeRepository repo) {
        return useCase(repo, NationalExchangeTestSupport.provinceAdmin());
    }

    private static SearchNationalExchangesUseCase useCase(FakeRepository repo, NationalExchangeAccessPolicy policy) {
        DefaultListableBeanFactory factory = new DefaultListableBeanFactory();
        if (repo != null) {
            factory.registerSingleton("repo", repo);
        }
        return new SearchNationalExchangesUseCase(factory.getBeanProvider(NationalExchangeQueryRepository.class),
            policy);
    }

    @Test
    void unitAdminIsForcedToOwnTenantIgnoringClientTenant() {
        FakeRepository repo = new FakeRepository();
        NationalExchangeSearchRequest request = new NationalExchangeSearchRequest();
        request.setTenantId("H05_02");
        useCase(repo, NationalExchangeTestSupport.policy("qtv", "H05_01", "ADMIN")).execute(request);
        assertThat(repo.request.getTenantId()).isEqualTo("H05_01");
    }

    @Test
    void provinceUserMayFilterAnyTenant() {
        FakeRepository repo = new FakeRepository();
        NationalExchangeSearchRequest request = new NationalExchangeSearchRequest();
        request.setTenantId("H05_02");
        useCase(repo).execute(request);
        assertThat(repo.request.getTenantId()).isEqualTo("H05_02");
    }

    @Test
    void listHidesErrorDetailWithoutAudit() {
        FakeRepository repo = new FakeRepository();
        repo.items.add(NationalExchangeItem.builder().errorCode("E1").errorDetail("stack").build());
        Result<NationalExchangeSearchResult> result = useCase(repo,
            NationalExchangeTestSupport.policy("vt", "H05", "NATIONAL_EXCHANGE_VIEW"))
            .execute(new NationalExchangeSearchRequest());
        assertThat(result.getData().getItems().get(0).getErrorCode()).isEqualTo("E1");
        assertThat(result.getData().getItems().get(0).getErrorDetail()).isNull();
    }

    @Test
    void userWithoutViewPermissionIsRejected() {
        Result<NationalExchangeSearchResult> result = useCase(new FakeRepository(),
            NationalExchangeTestSupport.policy("cv", "H05", "USER")).execute(new NationalExchangeSearchRequest());
        assertThat(result.getError()).isEqualTo(SearchNationalExchangesUseCase.FORBIDDEN);
    }

    @Test
    void notConfiguredReturnsBusinessFailureInsteadOfError() {
        Result<NationalExchangeSearchResult> result = useCase(null).execute(null);
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getError()).contains("NATIONAL_EXCHANGE_ADMIN_ENABLED");
    }

    @Test
    void repositoryFailureIsReportedAsUnavailable() {
        FakeRepository repo = new FakeRepository();
        repo.fail = true;
        Result<NationalExchangeSearchResult> result = useCase(repo).execute(new NationalExchangeSearchRequest());
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getError()).isEqualTo(SearchNationalExchangesUseCase.UNAVAILABLE);
    }

    @Test
    void defaultsToLast30DaysAndClampsPageSize() {
        FakeRepository repo = new FakeRepository();
        NationalExchangeSearchRequest request = new NationalExchangeSearchRequest();
        request.setPageNumber(3);
        request.setPageSize(1000);

        Result<NationalExchangeSearchResult> result = useCase(repo).execute(request);

        assertThat(result.isSuccess()).isTrue();
        assertThat(repo.to).isEqualTo(LocalDate.now());
        assertThat(repo.from).isEqualTo(LocalDate.now().minusDays(29));
        assertThat(repo.limit).isEqualTo(100);
        assertThat(repo.offset).isEqualTo(200);
        assertThat(result.getData().getPageNumber()).isEqualTo(3);
    }

    @Test
    void rejectsUnboundedOrInvertedRanges() {
        NationalExchangeSearchRequest inverted = new NationalExchangeSearchRequest();
        inverted.setFromDate("2026-10-10");
        inverted.setToDate("2026-10-01");
        assertThat(useCase(new FakeRepository()).execute(inverted).isSuccess()).isFalse();

        NationalExchangeSearchRequest tooLong = new NationalExchangeSearchRequest();
        tooLong.setFromDate("01/01/2024");
        tooLong.setToDate("01/01/2026");
        assertThat(useCase(new FakeRepository()).execute(tooLong).isSuccess()).isFalse();
    }

    @Test
    void rejectsUnknownFilterValues() {
        NationalExchangeSearchRequest badDirection = new NationalExchangeSearchRequest();
        badDirection.setDirection("SIDEWAYS");
        assertThat(useCase(new FakeRepository()).execute(badDirection).isSuccess()).isFalse();

        NationalExchangeSearchRequest badState = new NationalExchangeSearchRequest();
        badState.setStates(List.of("DONE", "DROP TABLE"));
        assertThat(useCase(new FakeRepository()).execute(badState).isSuccess()).isFalse();
    }

    @Test
    void blankFiltersAreNormalisedToNull() {
        NationalExchangeSearchRequest request = new NationalExchangeSearchRequest();
        request.setDirection(" ");
        request.setTenantId("");
        request.setKeyword("  H05.01  ");
        assertThat(SearchNationalExchangesUseCase.validate(request)).isNull();
        assertThat(request.getDirection()).isNull();
        assertThat(request.getTenantId()).isNull();
        assertThat(request.getKeyword()).isEqualTo("H05.01");
    }
}
