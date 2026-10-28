package com.antshorttv.points;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.antshorttv.security.TenantContextResolver;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class TeamPointTransactionFilterTest {
    private final JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(
        "jdbc:h2:mem:point_filter_" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", ""));
    private final TenantContextResolver access = mock(TenantContextResolver.class);
    private final TeamPointService service = new TeamPointService(jdbc, access, mock(PointAccountingService.class));

    @BeforeEach
    void setUp() {
        jdbc.execute("""
            create table point_ledger (id bigint, tenant_id bigint, user_id bigint, entry_type varchar(40),
                amount decimal(18,2), available_balance_after decimal(18,2), business_type varchar(40),
                business_id bigint, idempotency_key varchar(100), description varchar(100), created_at timestamp,
                reservation_id bigint, execution_id bigint)
            """);
        jdbc.execute("create table ai_point_reservation (id bigint,tenant_id bigint,execution_id bigint,scene varchar(100),business_type varchar(40),business_id bigint)");
        jdbc.execute("create table ai_execution_task (id bigint,tenant_id bigint,project_id bigint,scene varchar(100),business_type varchar(40),business_id bigint)");
        jdbc.execute("create table project (id bigint,tenant_id bigint,name varchar(100))");
        add(1, 10, "RELEASE", null);
        add(2, 10, "SETTLE", null);
        add(3, 10, "RELEASE", null);
        add(4, 20, "RELEASE", null);
        add(5, 10, "GRANT", "order:5");
        add(6, 10, "GRANT", "subscription:6");
        add(7, 10, "GRANT", null);
        add(8, 10, "GRANT", "manual:8");
        add(9, 10, "RESERVE", null);
        add(10, 10, "INCREMENTAL_RESERVE", null);
        add(11, 10, "REFUND", null);
        add(12, 10, "SETTLEMENT_REVIEW", null);
    }

    private void add(long id, long tenant, String type, String key) {
        jdbc.update("""
            insert into point_ledger (id, tenant_id, user_id, entry_type, amount, available_balance_after,
                idempotency_key, description, created_at) values (?, ?, 1, ?, 10, 100, ?, '测试流水', '2026-10-28 10:00:00')
            """, id, tenant, type, key);
    }

    @Test
    void filtersBeforeCountingAndPagingAndKeepsTenantIsolation() {
        var first = service.transactions(10L, 1, 1, TeamPointTransactionType.RELEASE);
        var second = service.transactions(10L, 2, 1, TeamPointTransactionType.RELEASE);
        assertThat(first.total()).isEqualTo(2);
        assertThat(first.records()).extracting(TeamPointTransactionResponse::id).containsExactly(3L);
        assertThat(second.total()).isEqualTo(2);
        assertThat(second.current()).isEqualTo(2);
        assertThat(second.records()).extracting(TeamPointTransactionResponse::id).containsExactly(1L);
        assertThat(service.transactions(10L, 3, 1, TeamPointTransactionType.RELEASE).records()).isEmpty();
        assertThat(service.transactions(10L, 1, 20).total()).isEqualTo(11);
        verify(access, times(4)).requireActiveMember(10L);
    }

    @Test
    void filtersEveryDisplayedTypeIncludingBothKindsOfGrant() {
        for (var type : TeamPointTransactionType.values()) {
            var page = service.transactions(10L, 1, 20, type);
            assertThat(page.records()).isNotEmpty().allSatisfy(row -> assertThat(row.transactionType()).isEqualTo(type.name()));
            assertThat(page.total()).isEqualTo((long) page.records().size());
        }
        assertThat(service.transactions(10L, 1, 20, TeamPointTransactionType.ENTITLEMENT_GRANT).records())
            .extracting(TeamPointTransactionResponse::id).containsExactly(6L, 5L);
        assertThat(service.transactions(10L, 1, 20, TeamPointTransactionType.ADJUST_GRANT).records())
            .extracting(TeamPointTransactionResponse::id).containsExactly(8L, 7L);
    }

    @Test
    void retainsPageBoundsAndChecksAccessBeforeQueries() {
        var defaults = service.transactions(10L, 0, 0, null);
        assertThat(defaults.current()).isEqualTo(1);
        assertThat(defaults.pageSize()).isEqualTo(20);
        assertThat(service.transactions(10L, 1, 500, null).pageSize()).isEqualTo(100);
        doThrow(new IllegalStateException("无权访问")).when(access).requireActiveMember(20L);
        assertThatThrownBy(() -> service.transactions(20L, 1, 20, TeamPointTransactionType.RELEASE))
            .isInstanceOf(IllegalStateException.class).hasMessage("无权访问");
    }

    @Test
    void bindsOptionalTypeAndRejectsUnknownTypes() throws Exception {
        var mockService = mock(TeamPointService.class);
        when(mockService.transactions(anyLong(), anyInt(), anyInt(), nullable(TeamPointTransactionType.class)))
            .thenReturn(new TeamPointTransactionPageResponse(List.of(), 0L, 1, 20));
        var mvc = MockMvcBuilders.standaloneSetup(new TeamPointController(mockService, mock(AiPointSettlementService.class), access)).build();
        mvc.perform(get("/api/tenants/10/points/transactions")).andExpect(status().isOk());
        verify(mockService).transactions(10L, 1, 20, null);
        mvc.perform(get("/api/tenants/10/points/transactions").param("transactionType", "RELEASE")
            .param("current", "2").param("pageSize", "50")).andExpect(status().isOk());
        verify(mockService).transactions(10L, 2, 50, TeamPointTransactionType.RELEASE);
        mvc.perform(get("/api/tenants/10/points/transactions").param("transactionType", "INVALID"))
            .andExpect(status().isBadRequest());
    }
}
