package com.td.application.tonghop.national;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.td.application.common.models.Result;
import com.td.application.common.security.CurrentUserProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static com.td.application.tonghop.national.NationalExchangeTestSupport.scopePolicy;
import static org.assertj.core.api.Assertions.assertThat;

class RequestNationalExchangeOperationUseCaseTest {

    /** Hàng đợi lệnh giả trong bộ nhớ. */
    static class FakeCommands implements NationalExchangeCommandRepository {
        final Map<String, NationalExchangeDetail.Operation> byKey = new HashMap<>();
        final List<NewOperation> created = new ArrayList<>();
        boolean active;

        @Override
        public Optional<NationalExchangeDetail.Operation> findByIdempotencyKey(String key) {
            return Optional.ofNullable(byKey.get(key));
        }

        @Override
        public boolean hasActive(String exchangeId, String type) {
            return active;
        }

        @Override
        public NationalExchangeDetail.Operation create(NewOperation op) {
            created.add(op);
            var view = NationalExchangeDetail.Operation.builder().requestId(op.requestId())
                .operationType(op.operationType()).status("PENDING").exchangeId(op.exchangeId()).build();
            byKey.put(op.idempotencyKey(), view);
            return view;
        }

        @Override
        public Optional<NationalExchangeDetail.Operation> find(String requestId) {
            return Optional.empty();
        }

        @Override
        public boolean cancel(String requestId, String exchangeId, String by, String byName) {
            return false;
        }
    }

    private static NationalExchangeDetail detail(String direction, String type, String status, String... tenants) {
        List<NationalExchangeDetail.Recipient> recipients = new ArrayList<>();
        for (String t : tenants) {
            recipients.add(NationalExchangeDetail.Recipient.builder().tenantId(t).agencyCode(t + ".code").build());
        }
        return NationalExchangeDetail.builder()
            .exchange(NationalExchangeItem.builder().exchangeId("X").direction(direction).messageType(type)
                .status(status).tenantId("OUTBOUND".equals(direction) ? "H05_01" : "manual-replay").build())
            .recipients(recipients)
            .build();
    }

    private static RequestNationalExchangeOperationUseCase useCase(NationalExchangeAccessPolicy policy,
                                                                   NationalExchangeDetail detail,
                                                                   FakeCommands commands) {
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
                return Optional.of(detail);
            }
        };
        DefaultListableBeanFactory factory = new DefaultListableBeanFactory();
        factory.registerSingleton("repo", repo);
        factory.registerSingleton("commands", commands);
        var detailUseCase = new GetNationalExchangeDetailUseCase(
            factory.getBeanProvider(NationalExchangeQueryRepository.class), policy);
        CurrentUserProvider user = new CurrentUserProvider() {
            @Override
            public Optional<String> currentUsername() {
                return Optional.of("qtv");
            }

            @Override
            public Set<String> currentRoles() {
                return Set.of();
            }
        };
        return new RequestNationalExchangeOperationUseCase(policy, detailUseCase,
            factory.getBeanProvider(NationalExchangeCommandRepository.class), user,
            new DefaultListableBeanFactory().getBeanProvider(
                com.td.application.sharedcore.GetUserOrganizationProfileUseCase.class),
            new ObjectMapper());
    }

    private static NationalExchangeAccessPolicy enabled(String username, String tenant, String... roles) {
        return new NationalExchangeAccessPolicy(scopePolicy(username, tenant, tenant, roles), true, "");
    }

    private static NationalExchangeOperationRequest op(String type, String reason, String... tenants) {
        NationalExchangeOperationRequest r = new NationalExchangeOperationRequest();
        r.setOperationType(type);
        r.setReason(reason);
        r.setTenantIds(List.of(tenants));
        return r;
    }

    private static final String REASON = "Đơn vị báo chưa nhận được văn bản";

    @Test
    void disabledOperationsAreRejected() {
        var policy = new NationalExchangeAccessPolicy(scopePolicy("admin", "H05", null));
        Result<?> r = useCase(policy, detail("OUTBOUND", "edoc", "FAILED"), new FakeCommands())
            .execute("OUT:u1", op("RESEND", REASON), null);
        assertThat(r.getError()).contains("NATIONAL_EXCHANGE_OPERATIONS_ENABLED");
    }

    @Test
    void resendByProvinceAdminCreatesPendingRequest() {
        FakeCommands commands = new FakeCommands();
        Result<NationalExchangeDetail.Operation> r = useCase(enabled("admin", "H05"),
            detail("OUTBOUND", "edoc", "FAILED"), commands).execute("OUT:u1", op("RESEND", REASON), "k1");
        assertThat(r.isSuccess()).isTrue();
        assertThat(commands.created).hasSize(1);
        assertThat(commands.created.get(0).requestedBy()).isEqualTo("qtv");
        assertThat(commands.created.get(0).idempotencyKey()).isEqualTo("k1");
    }

    @Test
    void sameIdempotencyKeyReturnsExistingRequest() {
        FakeCommands commands = new FakeCommands();
        var uc = useCase(enabled("admin", "H05"), detail("OUTBOUND", "edoc", "FAILED"), commands);
        var first = uc.execute("OUT:u1", op("RESEND", REASON), "k1");
        var second = uc.execute("OUT:u1", op("RESEND", REASON), "k1");
        assertThat(second.getData().getRequestId()).isEqualTo(first.getData().getRequestId());
        assertThat(commands.created).hasSize(1);
    }

    @Test
    void unitAdminCannotResendToAxis() {
        Result<?> r = useCase(enabled("qtv", "H05_01", "ADMIN"), detail("OUTBOUND", "edoc", "FAILED"),
            new FakeCommands()).execute("OUT:u1", op("RESEND", REASON), null);
        assertThat(r.getError()).contains("NATIONAL_EXCHANGE_RESEND");
    }

    @Test
    void reasonIsMandatory() {
        Result<?> r = useCase(enabled("admin", "H05"), detail("OUTBOUND", "edoc", "FAILED"), new FakeCommands())
            .execute("OUT:u1", op("RESEND", "ngắn"), null);
        assertThat(r.getError()).contains("Lý do");
    }

    @Test
    void preconditionsFollowPackageState() {
        assertThat(RequestNationalExchangeOperationUseCase.precondition("RESEND",
            detail("OUTBOUND", "edoc", "SENT").getExchange())).isNotNull();
        assertThat(RequestNationalExchangeOperationUseCase.precondition("RESEND",
            detail("OUTBOUND", "edoc", "BUILT_ONLY").getExchange())).isNull();
        assertThat(RequestNationalExchangeOperationUseCase.precondition("RECEIVE_AGAIN",
            detail("INBOUND", "edoc", "RECEIVED").getExchange())).contains("Phát lại");
        assertThat(RequestNationalExchangeOperationUseCase.precondition("REPLAY_INTERNAL",
            detail("INBOUND", "edoc", "AWAITING_ACK").getExchange())).isNull();
        assertThat(RequestNationalExchangeOperationUseCase.precondition("REPROCESS_STATUS",
            detail("INBOUND", "status", "QUARANTINED").getExchange())).isNull();
    }

    @Test
    void unitAdminReplayIsForcedToOwnTenant() {
        FakeCommands commands = new FakeCommands();
        Result<?> r = useCase(enabled("qtv", "H05_01", "ADMIN"),
            detail("INBOUND", "edoc", "RECEIVED", "H05_01", "H05_02"), commands)
            .execute("IN:d1", op("REPLAY_INTERNAL", REASON, "H05_02"), null);
        assertThat(r.isSuccess()).isTrue();
        assertThat(commands.created.get(0).parametersJson()).contains("\"tenantIds\":[\"H05_01\"]");
    }

    @Test
    void provinceReplayRejectsTenantOutsideRecipients() {
        Result<?> r = useCase(enabled("admin", "H05"), detail("INBOUND", "edoc", "RECEIVED", "H05_01"),
            new FakeCommands()).execute("IN:d1", op("REPLAY_INTERNAL", REASON, "H05_09"), null);
        assertThat(r.getError()).contains("không thuộc danh sách nơi nhận");
    }

    @Test
    void duplicateActiveRequestIsBlocked() {
        FakeCommands commands = new FakeCommands();
        commands.active = true;
        Result<?> r = useCase(enabled("admin", "H05"), detail("OUTBOUND", "edoc", "FAILED"), commands)
            .execute("OUT:u1", op("RESEND", REASON), null);
        assertThat(r.getError()).contains("đang chờ");
    }
}
