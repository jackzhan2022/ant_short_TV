package com.antshorttv.commercial;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CommercialOrderService {
    private final CommercialPackageVersionMapper versionMapper;
    private final CommercialOrderMapper orderMapper;
    private final CommercialPaymentMapper paymentMapper;

    public CommercialOrderService(CommercialPackageVersionMapper versionMapper, CommercialOrderMapper orderMapper, CommercialPaymentMapper paymentMapper) {
        this.versionMapper = versionMapper; this.orderMapper = orderMapper; this.paymentMapper = paymentMapper;
    }

    @Transactional
    public CommercialOrderResponse create(CommercialOrderCommand command) {
        CommercialPackageVersionEntity version = versionMapper.selectById(command.packageVersionId());
        if (version == null || !"PUBLISHED".equals(version.status)) throw new IllegalArgumentException("Package version is not for sale");
        LocalDateTime now = LocalDateTime.now();
        CommercialOrderEntity order = new CommercialOrderEntity(); order.tenantId = command.tenantId(); order.userId = command.userId(); order.packageVersionId = version.id; order.packageSnapshotJson = "{\"name\":\"" + version.name.replace("\"", "\\\"") + "\",\"versionNo\":" + version.versionNo + "}"; order.merchantOrderNo = "COM" + UUID.randomUUID().toString().replace("-", ""); order.amount = version.price; order.currency = version.currency; order.status = "PENDING_PAYMENT"; order.expiresAt = now.plusMinutes(30); order.createdAt = now; order.updatedAt = now; orderMapper.insert(order);
        CommercialPaymentEntity payment = new CommercialPaymentEntity(); payment.orderId = order.id; payment.provider = "WECHAT_NATIVE"; payment.amount = version.price; payment.status = "PENDING"; payment.createdAt = now; payment.updatedAt = now; paymentMapper.insert(payment);
        return new CommercialOrderResponse(order.id, order.merchantOrderNo, order.amount, order.currency, order.status, order.expiresAt, null);
    }
    public CommercialOrderEntity require(Long id) { CommercialOrderEntity order = orderMapper.selectById(id); if (order == null) throw new IllegalArgumentException("Order not found"); return order; }
}

record CommercialOrderCommand(Long tenantId, Long userId, Long packageVersionId) {}
record CommercialOrderResponse(Long id, String merchantOrderNo, java.math.BigDecimal amount, String currency, String status, LocalDateTime expiresAt, String codeUrl) {}
