package com.antshorttv.common;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public final class MediaPageQueries {
    private MediaPageQueries() {}

    public static <T> MediaPage<T> select(BaseMapper<T> mapper, QueryWrapper<T> query, PageBounds page) {
        return select(mapper, query, page, q -> q.orderByDesc("created_at", "id"));
    }

    public static <T> MediaPage<T> select(
        BaseMapper<T> mapper, QueryWrapper<T> query, PageBounds page, Consumer<QueryWrapper<T>> projectionAndOrder
    ) {
        long total = mapper.selectCount(query);
        if (page.offset() >= total) {
            return new MediaPage<>(List.of(), page.current(), page.pageSize(), total);
        }
        projectionAndOrder.accept(query);
        query.last("limit " + page.pageSize() + " offset " + page.offset());
        return new MediaPage<>(mapper.selectList(query), page.current(), page.pageSize(), total);
    }

    public static <T> QueryWrapper<T> projectQuery(Long tenantId, Long projectId) {
        return new QueryWrapper<T>().eq("tenant_id", tenantId).eq("project_id", projectId);
    }

    public static <T> Map<Long, Long> resultCounts(
        BaseMapper<T> mapper, Long tenantId, Long projectId, List<Long> taskIds, String status, String taskColumn
    ) {
        if (taskIds.isEmpty()) return Map.of();
        var query = MediaPageQueries.<T>projectQuery(tenantId, projectId)
            .select(taskColumn, "count(*) as result_count").in(taskColumn, taskIds)
            .groupBy(taskColumn);
        if (status != null) query.eq("status", status);
        var rows = mapper.selectMaps(query);
        Map<Long, Long> counts = new HashMap<>();
        for (var row : rows) {
            counts.put(number(row, taskColumn), number(row, "result_count"));
        }
        return counts;
    }

    public static <T> QueryWrapper<T> representativeQuery(
        String table, Long tenantId, Long projectId, List<Long> taskIds, String status, String selectedColumn
    ) {
        QueryWrapper<T> query = MediaPageQueries.<T>projectQuery(tenantId, projectId).eq("status", status);
        if (taskIds.isEmpty()) return query.apply("1 = 0");
        String ids = taskIds.stream().map(String::valueOf).collect(Collectors.joining(","));
        String grouped = "select max(id) from " + table + " where tenant_id = " + tenantId
            + " and project_id = " + projectId + " and status = '" + status
            + "' and task_id in (" + ids + ")";
        // Each task contributes its latest result and, when distinct, its latest selected result.
        return query.in("task_id", taskIds).and(q -> q
            .inSql("id", grouped + " group by task_id")
            .or().inSql("id", grouped + " and " + selectedColumn + " = true group by task_id"))
            .orderByDesc("id");
    }

    private static long number(Map<String, Object> row, String column) {
        return row.entrySet().stream().filter(e -> e.getKey().equalsIgnoreCase(column))
            .map(e -> ((Number) e.getValue()).longValue()).findFirst().orElseThrow();
    }
}
