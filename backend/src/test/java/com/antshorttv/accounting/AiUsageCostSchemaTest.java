package com.antshorttv.accounting;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class AiUsageCostSchemaTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void createsImmutableUsagePricingAndCostSnapshotTables() {
        Integer tables = jdbc.queryForObject("""
            select count(distinct lower(table_name))
              from information_schema.tables
             where lower(table_name) in (
               'ai_usage_line', 'ai_model_price_version',
               'ai_model_price_component', 'ai_usage_cost_line'
             )
            """, Integer.class);
        Integer usageColumns = jdbc.queryForObject("""
            select count(distinct lower(column_name))
              from information_schema.columns
             where lower(table_name) = 'ai_usage_line'
               and lower(column_name) in (
                 'execution_id', 'attempt_id', 'ai_call_log_id', 'model_id', 'metric',
                 'quantity', 'unit', 'source', 'dimensions_json', 'observed_at',
                 'adjustment_of_usage_line_id'
               )
            """, Integer.class);
        Integer costColumns = jdbc.queryForObject("""
            select count(distinct lower(column_name))
              from information_schema.columns
             where lower(table_name) = 'ai_usage_cost_line'
               and lower(column_name) in (
                 'usage_line_id', 'price_version_id', 'price_component_id', 'metric',
                 'quantity', 'unit_size', 'unit_price', 'currency', 'raw_cost',
                 'rounded_cost', 'pricing_status', 'adjustment_of_cost_line_id'
               )
            """, Integer.class);

        assertThat(tables).isEqualTo(4);
        assertThat(usageColumns).isEqualTo(11);
        assertThat(costColumns).isEqualTo(12);
    }
}
