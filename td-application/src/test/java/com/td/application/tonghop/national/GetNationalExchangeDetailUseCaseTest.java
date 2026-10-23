package com.td.application.tonghop.national;

import com.td.application.common.models.Result;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.td.application.tonghop.national.NationalExchangeTestSupport.policy;
import static com.td.application.tonghop.national.NationalExchangeTestSupport.provinceAdmin;
import static org.assertj.core.api.Assertions.assertThat;

class GetNationalExchangeDetailUseCaseTest {

    /** Gói đến bàn giao cho 2 tenant H05_01 và H05_02, master tenant "manual-replay". */
    private static NationalExchangeDetail inboundForTwoTenants() {
        return NationalExchangeDetail.builder()
            .exchange(NationalExchangeItem.builder()
                .exchangeId("IN:pkg").direction("INBOUND").tenantId("manual-replay")
                .recipientCodes("H05.01;H05.02").recipientCount(2).acknowledgedCount(1)
                .errorCode("E1").errorDetail("stack trace").build())
            .dynamicFields(Map.of("packageVariant", "x"))
            .successDetail("ok")
            .recipients(new ArrayList<>(List.of(
                NationalExchangeDetail.Recipient.builder().tenantId("H05_01").agencyCode("H05.01")
                    .deliveryStatus("RECEIVED").errorDetail("e1").build(),
                NationalExchangeDetail.Recipient.builder().tenantId("H05_02").agencyCode("H05.02")
                    .deliveryStatus("AWAITING_ACK").build())))
            .events(new ArrayList<>(List.of(
                NationalExchangeDetail.Event.builder().eventType("PACKAGE_DOWNLOADED").tenantId("manual-replay")
                    .details("d").build(),
                NationalExchangeDetail.Event.builder().eventType("RECIPIENT_ACKNOWLEDGED").tenantId("H05_02")
                    .agencyCode("H05.02").build())))
            .attempts(new ArrayList<>(List.of(NationalExchangeDetail.Attempt.builder().attemptId(1).build())))
            .operations(new ArrayList<>(List.of(NationalExchangeDetail.Operation.builder()
                .requestId("r").requestedBy("admin").requestedByName("Quản trị").build())))
            .build();
    }

    private static GetNationalExchangeDetailUseCase useCase(NationalExchangeAccessPolicy policy) {
        NationalExchangeQueryRepository repo = new NationalExchangeQueryRepository() {
            @Override
            public NationalExchangeSearchResult search(NationalExchangeSearchRequest r, LocalDate f, LocalDate t,
                                                       int o, int l) {
                throw new UnsupportedOperationException();
            }

            @Override
            public NationalExchangeSummary summary(NationalExchangeSearchRequest r, LocalDate f, LocalDate t) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Optional<NationalExchangeDetail> findDetail(String exchangeId) {
                return Optional.of(inboundForTwoTenants());
            }
        };
        DefaultListableBeanFactory factory = new DefaultListableBeanFactory();
        factory.registerSingleton("repo", repo);
        return new GetNationalExchangeDetailUseCase(factory.getBeanProvider(NationalExchangeQueryRepository.class),
            policy);
    }

    @Test
    void provinceAdminSeesEverything() {
        NationalExchangeDetail d = useCase(provinceAdmin()).execute("IN:pkg").getData();
        assertThat(d.getRecipients()).hasSize(2);
        assertThat(d.getEvents()).hasSize(2);
        assertThat(d.getAttempts()).hasSize(1);
        assertThat(d.getExchange().getErrorDetail()).isEqualTo("stack trace");
    }

    @Test
    void unitAdminOnlySeesOwnRecipientAndEvents() {
        NationalExchangeDetail d = useCase(policy("qtv", "H05_01", "ADMIN")).execute("IN:pkg").getData();
        assertThat(d.getRecipients()).extracting(NationalExchangeDetail.Recipient::getTenantId)
            .containsExactly("H05_01");
        assertThat(d.getEvents()).extracting(NationalExchangeDetail.Event::getEventType)
            .containsExactly("PACKAGE_DOWNLOADED");
        assertThat(d.getExchange().getRecipientCodes()).isEqualTo("H05.01");
        assertThat(d.getExchange().getRecipientCount()).isEqualTo(1);
        assertThat(d.getExchange().getAcknowledgedCount()).isEqualTo(1);
    }

    @Test
    void otherTenantGetsNotFound() {
        Result<NationalExchangeDetail> result = useCase(policy("qtv", "H05_09", "ADMIN")).execute("IN:pkg");
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getError()).isEqualTo(GetNationalExchangeDetailUseCase.NOT_FOUND);
    }

    @Test
    void viewWithoutAuditMasksTechnicalData() {
        NationalExchangeDetail d = useCase(policy("vt", "H05", "NATIONAL_EXCHANGE_VIEW")).execute("IN:pkg").getData();
        assertThat(d.getExchange().getErrorCode()).isEqualTo("E1");
        assertThat(d.getExchange().getErrorDetail()).isNull();
        assertThat(d.getAttempts()).isEmpty();
        assertThat(d.getDynamicFields()).isEmpty();
        assertThat(d.getSuccessDetail()).isNull();
        assertThat(d.getRecipients()).allSatisfy(r -> assertThat(r.getErrorDetail()).isNull());
        assertThat(d.getEvents()).allSatisfy(e -> assertThat(e.getDetails()).isNull());
        assertThat(d.getOperations()).allSatisfy(o -> {
            assertThat(o.getRequestedBy()).isNull();
            assertThat(o.getRequestedByName()).isNull();
        });
    }

    @Test
    void userWithoutPermissionIsRejected() {
        Result<NationalExchangeDetail> result = useCase(policy("cv", "H05", "USER")).execute("IN:pkg");
        assertThat(result.getError()).isEqualTo(SearchNationalExchangesUseCase.FORBIDDEN);
    }
}
