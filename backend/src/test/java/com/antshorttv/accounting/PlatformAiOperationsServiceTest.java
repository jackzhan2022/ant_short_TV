package com.antshorttv.accounting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.antshorttv.security.CurrentPrincipal;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import static com.antshorttv.accounting.PlatformAiOperationsResponses.PlatformAiOperationsOverview;

class PlatformAiOperationsServiceTest {
    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final CurrentPrincipal currentPrincipal = mock(CurrentPrincipal.class);
    private final PlatformAiOperationsService service = new PlatformAiOperationsService(jdbcTemplate, currentPrincipal);

    @Test
    void aggregatesOperationalCountersAndProviderFailureRates() {
        when(jdbcTemplate.queryForObject(anyString(), any(Class.class)))
            .thenReturn(2L, 3L, 4L, 5L, 6L, new BigDecimal("12.50"), new BigDecimal("18"));
        when(jdbcTemplate.query(anyString(), any(RowMapper.class))).thenAnswer(invocation -> {
            RowMapper<?> mapper = invocation.getArgument(1);
            ResultSet rs = mock(ResultSet.class);
            when(rs.getString("provider")).thenReturn("OpenAI");
            when(rs.getLong("total")).thenReturn(4L);
            when(rs.getLong("failed")).thenReturn(1L);
            return List.of(mapper.mapRow(rs, 0));
        });

        PlatformAiOperationsOverview result = service.overview();

        assertThat(result.expiredClaims()).isEqualTo(2);
        assertThat(result.retryExhausted()).isEqualTo(3);
        assertThat(result.unpricedUsage()).isEqualTo(4);
        assertThat(result.incompleteUsage()).isEqualTo(5);
        assertThat(result.settlementReview()).isEqualTo(6);
        assertThat(result.totalProviderCost()).isEqualByComparingTo("12.50");
        assertThat(result.totalSettledPoints()).isEqualByComparingTo("18");
        assertThat(result.providerFailureRates()).singleElement().satisfies(rate -> {
            assertThat(rate.provider()).isEqualTo("OpenAI");
            assertThat(rate.failureRate()).isEqualByComparingTo("0.2500");
        });
    }
}
