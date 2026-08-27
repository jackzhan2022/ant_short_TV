package com.antshorttv.commercial;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CommercialOrderService {
    private final CommercialPackageVersionMapper versionMapper;
    private final CommercialOrderMapper orderMapper;
    private final CommercialPaymentMapper paymentMapper;
    private final WechatPayClient wechatPayClient;
    private final WechatPayProperties wechatPayProperties;

    public CommercialOrderService(CommercialPackageVersionMapper versionMapper, CommercialOrderMapper orderMapper, CommercialPaymentMapper paymentMapper, WechatPayClient wechatPayClient, WechatPayProperties wechatPayProperties) {
        this.versionMapper = versionMapper; this.orderMapper = orderMapper; this.paymentMapper = paymentMapper; this.wechatPayClient = wechatPayClient; this.wechatPayProperties = wechatPayProperties;
    }

    @Transactional
    public CommercialOrderResponse create(CommercialOrderCommand command) {
        CommercialPackageVersionEntity version = versionMapper.selectById(command.packageVersionId());
        if (version == null || !"PUBLISHED".equals(version.status)) throw new IllegalArgumentException("Package version is not for sale");
        LocalDateTime now = LocalDateTime.now();
        CommercialOrderEntity order = new CommercialOrderEntity(); order.tenantId = command.tenantId(); order.userId = command.userId(); order.packageVersionId = version.id; order.packageSnapshotJson = "{\"name\":\"" + version.name.replace("\"", "\\\"") + "\",\"versionNo\":" + version.versionNo + "}"; order.merchantOrderNo = "COM" + UUID.randomUUID().toString().replace("-", ""); order.amount = version.price; order.currency = version.currency; order.status = "PENDING_PAYMENT"; order.expiresAt = now.plusMinutes(30); order.createdAt = now; order.updatedAt = now; orderMapper.insert(order);
        CommercialPaymentEntity payment = new CommercialPaymentEntity(); payment.orderId = order.id; payment.provider = "WECHAT_NATIVE"; payment.amount = version.price; payment.status = "PENDING"; payment.createdAt = now; payment.updatedAt = now; paymentMapper.insert(payment);
        if (wechatPayProperties.isEnabled()) {
            WechatNativeOrder nativeOrder = wechatPayClient.createNativeOrder(new WechatNativeOrderRequest(order.merchantOrderNo, version.name, order.amount, order.currency));
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
}

record CommercialOrderCommand(Long tenantId, Long userId, Long packageVersionId) {}
record CommercialOrderResponse(Long id, String merchantOrderNo, java.math.BigDecimal amount, String currency, String status, LocalDateTime expiresAt, String codeUrl) {}
