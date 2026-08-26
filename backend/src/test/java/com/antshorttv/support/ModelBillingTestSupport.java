package com.antshorttv.support;

import java.math.BigDecimal;
import org.springframework.jdbc.core.JdbcTemplate;

public final class ModelBillingTestSupport {
    private ModelBillingTestSupport() {
    }

    public static void publish(
        JdbcTemplate jdbc,
        Long modelId,
        String metric,
        BigDecimal unitSize,
        BigDecimal pointRate
    ) {
        Long costVersionId = jdbc.query(
            "select id from ai_model_price_version where model_id = ? and status = 'PUBLISHED' order by version_no desc limit 1",
            rs -> rs.next() ? rs.getLong(1) : null,
            modelId
        );
        if (costVersionId == null) {
            jdbc.update("""
                insert into ai_model_price_version
                  (model_id, version_no, status, effective_from, published_at, created_at)
                values (?, 1, 'PUBLISHED', dateadd('hour', -1, now()), now(), now())
                """, modelId);
            costVersionId = jdbc.queryForObject(
                "select max(id) from ai_model_price_version where model_id = ?", Long.class, modelId
            );
        }
        if (componentCount(jdbc, "ai_model_price_component", costVersionId, metric) == 0) {
            jdbc.update("""
                insert into ai_model_price_component
                  (price_version_id, metric, unit_size, unit_price, currency,
                   dimensions_json, dimensions_key, created_at)
                values (?, ?, ?, 0.1, 'USD', '{}', '', now())
                """, costVersionId, metric, unitSize);
        }

        Long pointVersionId = jdbc.query(
            "select id from ai_model_point_price_version where model_id = ? and status = 'PUBLISHED' order by version_no desc limit 1",
            rs -> rs.next() ? rs.getLong(1) : null,
            modelId
        );
        if (pointVersionId == null) {
            jdbc.update("""
                insert into ai_model_point_price_version
                  (model_id, version_no, status, effective_from, published_at, created_at)
                values (?, 1, 'PUBLISHED', dateadd('hour', -1, now()), now(), now())
                """, modelId);
            pointVersionId = jdbc.queryForObject(
                "select max(id) from ai_model_point_price_version where model_id = ?", Long.class, modelId
            );
        }
        if (componentCount(jdbc, "ai_model_point_price_component", pointVersionId, metric) == 0) {
            jdbc.update("""
                insert into ai_model_point_price_component
                  (price_version_id, metric, unit_size, point_rate,
                   dimensions_json, dimensions_key, created_at)
                values (?, ?, ?, ?, '{}', '', now())
                """, pointVersionId, metric, unitSize, pointRate);
        }
    }

    private static int componentCount(JdbcTemplate jdbc, String table, Long versionId, String metric) {
        return jdbc.queryForObject(
            "select count(*) from " + table + " where price_version_id = ? and metric = ?",
            Integer.class,
            versionId,
            metric
        );
    }
}
