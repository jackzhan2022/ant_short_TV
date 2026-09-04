package com.antshorttv.commercial;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CommercialOrderService {
    private final CommercialPackageVersionMapper versionMapper;
    private final CommercialOrderMapper orderMapper;
    private final CommercialPaymentMapper paymentMapper;
    private final WechatPayClient wechatPayClient;
    private final WechatPayProperties wechatPayProperties;
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public CommercialOrderService(CommercialPackageVersionMapper versionMapper, CommercialOrderMapper orderMapper, CommercialPaymentMapper paymentMapper, WechatPayClient wechatPayClient, WechatPayProperties wechatPayProperties, JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.versionMapper = versionMapper; this.orderMapper = orderMapper; this.paymentMapper = paymentMapper; this.wechatPayClient = wechatPayClient; this.wechatPayProperties = wechatPayProperties; this.jdbc = jdbc; this.objectMapper = objectMapper;
    }

    @Transactional
    public CommercialOrderResponse create(CommercialOrderCommand command) {
        CommercialPackageVersionEntity version = versionMapper.selectById(command.packageVersionId());
        if (version == null || !"PUBLISHED".equals(version.status)) throw new IllegalArgumentException("Package version is not for sale");
        LocalDateTime now = LocalDateTime.now();
        CommercialOrderEntity order = new CommercialOrderEntity(); order.tenantId = command.tenantId(); order.userId = command.userId(); order.packageVersionId = version.id; order.packageSnapshotJson = "{\"name\":\"" + version.name.replace("\"", "\\\"") + "\",\"versionNo\":" + version.versionNo + "}"; order.merchantOrderNo = generateMerchantOrderNo(); order.amount = version.price; order.currency = version.currency; order.status = "PENDING_PAYMENT"; order.expiresAt = now.plusMinutes(30); order.createdAt = now; order.updatedAt = now; orderMapper.insert(order);
        CommercialPaymentEntity payment = new CommercialPaymentEntity(); payment.orderId = order.id; payment.provider = "WECHAT_NATIVE"; payment.amount = version.price; payment.status = "PENDING"; payment.createdAt = now; payment.updatedAt = now; paymentMapper.insert(payment);
        if (wechatPayProperties.isEnabled()) {
            WechatNativeOrder nativeOrder;
            try { nativeOrder = wechatPayClient.createNativeOrder(new WechatNativeOrderRequest(order.merchantOrderNo, version.name, order.amount, order.currency)); }
            catch (IllegalStateException exception) { throw new BusinessException(ErrorCode.VALIDATION_ERROR, "微信支付订单创建失败，请稍后重试。"); }
            payment.prepayId = nativeOrder.prepayId(); payment.codeUrl = nativeOrder.codeUrl(); payment.updatedAt = LocalDateTime.now(); paymentMapper.updateById(payment);
        }
        return new CommercialOrderResponse(order.id, order.merchantOrderNo, order.amount, order.currency, order.status, order.expiresAt, payment.codeUrl);
    }
    public CommercialOrderEntity require(Long id) { CommercialOrderEntity order = orderMapper.selectById(id); if (order == null) throw new IllegalArgumentException("Order not found"); return order; }
    public List<CommercialOrderResponse> active(Long tenantId) {
        return orderMapper.selectList(new QueryWrapper<CommercialOrderEntity>()
            .eq("tenant_id", tenantId).in("status", List.of("PENDING_PAYMENT", "ENTITLEMENT_PENDING"))
            .orderByDesc("created_at")).stream().map(this::response).toList();
    }
    public CommercialOrderResponse response(CommercialOrderEntity order) {
        CommercialPaymentEntity payment = paymentMapper.selectOne(new QueryWrapper<CommercialPaymentEntity>().eq("order_id", order.id));
        return new CommercialOrderResponse(order.id, order.merchantOrderNo, order.amount, order.currency, order.status, order.expiresAt, payment == null ? null : payment.codeUrl);
    }
    public PlatformCommercialOrderPageResponse listPlatform(PlatformCommercialOrderQuery query) {
        SqlAndArgs filtered = filteredPlatformOrders(query);
        Long total = jdbc.queryForObject("select count(*) " + filtered.sql(), Long.class, filtered.args().toArray());
        List<Object> pageArgs = new ArrayList<>(filtered.args());
        pageArgs.add(query.pageSize());
        pageArgs.add((query.current() - 1) * query.pageSize());
        List<PlatformCommercialOrderSummaryResponse> records = jdbc.query(selectPlatformOrders() + filtered.sql()
                + " order by o.created_at desc, o.id desc limit ? offset ?",
            (rs, rowNum) -> summary(rs), pageArgs.toArray());
        return new PlatformCommercialOrderPageResponse(records, total == null ? 0 : total, query.current(), query.pageSize());
    }
    public PlatformCommercialOrderDetailResponse platformDetail(Long orderId) {
        List<PlatformCommercialOrderDetailResponse> details = jdbc.query(selectPlatformOrders()
                + " from commercial_order o left join tenant t on t.id=o.tenant_id"
                + " join commercial_package_version v on v.id=o.package_version_id"
                + " join commercial_package p on p.id=v.package_id"
                + " left join commercial_payment pay on pay.order_id=o.id where o.id=?",
            (rs, rowNum) -> detail(rs), orderId);
        if (details.isEmpty()) throw new IllegalArgumentException("Order not found");
        return details.get(0);
    }
    private SqlAndArgs filteredPlatformOrders(PlatformCommercialOrderQuery query) {
        StringBuilder sql = new StringBuilder(" from commercial_order o left join tenant t on t.id=o.tenant_id"
            + " join commercial_package_version v on v.id=o.package_version_id"
            + " join commercial_package p on p.id=v.package_id"
            + " left join commercial_payment pay on pay.order_id=o.id where 1=1");
        List<Object> args = new ArrayList<>();
        if (query.keyword() != null) {
            String keyword = "%" + query.keyword().toLowerCase() + "%";
            sql.append(" and (lower(o.merchant_order_no) like ? or lower(t.name) like ? or lower(t.code) like ? or lower(v.name) like ?)");
            args.add(keyword); args.add(keyword); args.add(keyword); args.add(keyword);
        }
        if (query.status() != null) { sql.append(" and o.status=?"); args.add(query.status()); }
        if (query.packageType() != null) { sql.append(" and p.package_type=?"); args.add(query.packageType()); }
        return new SqlAndArgs(sql.toString(), args);
    }
    private String selectPlatformOrders() {
        return "select o.id,o.merchant_order_no,o.tenant_id,o.package_version_id,o.package_snapshot_json,o.amount,o.currency,o.status order_status,"
            + "o.expires_at,o.paid_at order_paid_at,o.completed_at,o.created_at order_created_at,o.updated_at order_updated_at,"
            + "t.name tenant_name,t.code tenant_code,v.name package_name,v.version_no,p.package_type,"
            + "pay.provider,pay.provider_trade_no,pay.status payment_status,pay.paid_at payment_paid_at ";
    }
    private PlatformCommercialOrderSummaryResponse summary(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new PlatformCommercialOrderSummaryResponse(rs.getLong("id"), rs.getString("merchant_order_no"),
            rs.getLong("tenant_id"), rs.getString("tenant_name"), rs.getString("tenant_code"), rs.getLong("package_version_id"),
            snapshotName(rs.getString("package_snapshot_json"), rs.getString("package_name")), rs.getInt("version_no"), rs.getString("package_type"), rs.getBigDecimal("amount"),
            rs.getString("currency"), rs.getString("order_status"), localDateTime(rs.getTimestamp("order_paid_at")),
            localDateTime(rs.getTimestamp("order_created_at")), payment(rs));
    }
    private PlatformCommercialOrderDetailResponse detail(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new PlatformCommercialOrderDetailResponse(rs.getLong("id"), rs.getString("merchant_order_no"),
            rs.getLong("tenant_id"), rs.getString("tenant_name"), rs.getString("tenant_code"), rs.getLong("package_version_id"),
            snapshotName(rs.getString("package_snapshot_json"), rs.getString("package_name")), rs.getInt("version_no"), rs.getString("package_type"), rs.getBigDecimal("amount"),
            rs.getString("currency"), rs.getString("order_status"), localDateTime(rs.getTimestamp("expires_at")),
            localDateTime(rs.getTimestamp("order_paid_at")), localDateTime(rs.getTimestamp("completed_at")),
            localDateTime(rs.getTimestamp("order_created_at")), localDateTime(rs.getTimestamp("order_updated_at")), payment(rs));
    }
    private PlatformCommercialOrderPaymentResponse payment(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new PlatformCommercialOrderPaymentResponse(rs.getString("provider"), rs.getString("provider_trade_no"),
            rs.getString("payment_status"), localDateTime(rs.getTimestamp("payment_paid_at")));
    }
    private LocalDateTime localDateTime(java.sql.Timestamp value) { return value == null ? null : value.toLocalDateTime(); }
    private String snapshotName(String snapshotJson, String fallback) {
        try {
            JsonNode name = objectMapper.readTree(snapshotJson).get("name");
            return name != null && name.isTextual() && !name.asText().isBlank() ? name.asText() : fallback;
        } catch (Exception ignored) {
            return fallback;
        }
    }
    private record SqlAndArgs(String sql, List<Object> args) {}
    private String generateMerchantOrderNo() { return "COM" + UUID.randomUUID().toString().replace("-", "").substring(0, 29); }
}

record CommercialOrderCommand(Long tenantId, Long userId, Long packageVersionId) {}
record CommercialOrderResponse(Long id, String merchantOrderNo, java.math.BigDecimal amount, String currency, String status, LocalDateTime expiresAt, String codeUrl) {}
