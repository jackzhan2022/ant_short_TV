package com.antshorttv.commercial;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CommercialEntitlementCatalogService {
    static final String SYSTEM = "SYSTEM";
    static final String DISPLAY = "DISPLAY";
    static final String ACTIVE = "ACTIVE";
    static final String INACTIVE = "INACTIVE";

    private final CommercialEntitlementDefinitionMapper mapper;

    public CommercialEntitlementCatalogService(CommercialEntitlementDefinitionMapper mapper) {
        this.mapper = mapper;
    }

    public List<CommercialEntitlementDefinitionResponse> list() {
        return mapper.selectList(new QueryWrapper<CommercialEntitlementDefinitionEntity>()
                .orderByAsc("sort_order", "id"))
            .stream().map(this::response).toList();
    }

    @Transactional
    public CommercialEntitlementDefinitionResponse create(CommercialDisplayEntitlementCommand command) {
        DisplayValues values = validate(command, null, true);
        CommercialEntitlementDefinitionEntity entity = new CommercialEntitlementDefinitionEntity();
        entity.code = generateCode();
        entity.name = values.name();
        entity.description = values.description();
        entity.category = DISPLAY;
        entity.status = ACTIVE;
        entity.sortOrder = values.sortOrder();
        entity.createdAt = LocalDateTime.now();
        entity.updatedAt = entity.createdAt;
        mapper.insert(entity);
        return response(entity);
    }

    @Transactional
    public CommercialEntitlementDefinitionResponse update(
        Long id, CommercialDisplayEntitlementCommand command
    ) {
        CommercialEntitlementDefinitionEntity entity = requireDisplay(id);
        DisplayValues values = validate(command, id, ACTIVE.equals(entity.status));
        entity.name = values.name();
        entity.description = values.description();
        entity.sortOrder = values.sortOrder();
        entity.updatedAt = LocalDateTime.now();
        mapper.updateById(entity);
        return response(entity);
    }

    @Transactional
    public CommercialEntitlementDefinitionResponse enable(Long id) {
        CommercialEntitlementDefinitionEntity entity = requireDisplay(id);
        ensureUniqueActiveName(entity.name, id);
        return updateStatus(entity, ACTIVE);
    }

    @Transactional
    public CommercialEntitlementDefinitionResponse disable(Long id) {
        return updateStatus(requireDisplay(id), INACTIVE);
    }

    CommercialEntitlementDefinitionEntity requireByCode(String code) {
        CommercialEntitlementDefinitionEntity entity = mapper.selectOne(
            new QueryWrapper<CommercialEntitlementDefinitionEntity>().eq("code", code));
        if (entity == null) throw validation("权益不存在：" + code);
        return entity;
    }

    private CommercialEntitlementDefinitionResponse updateStatus(
        CommercialEntitlementDefinitionEntity entity, String status
    ) {
        entity.status = status;
        entity.updatedAt = LocalDateTime.now();
        mapper.updateById(entity);
        return response(entity);
    }

    private CommercialEntitlementDefinitionEntity requireDisplay(Long id) {
        CommercialEntitlementDefinitionEntity entity = mapper.selectById(id);
        if (entity == null) throw new BusinessException(ErrorCode.NOT_FOUND, "权益不存在。");
        if (SYSTEM.equals(entity.category)) throw validation("系统权益不可修改。");
        return entity;
    }

    private DisplayValues validate(
        CommercialDisplayEntitlementCommand command, Long excludedId, boolean enforceUniqueName
    ) {
        if (command == null || command.name() == null || command.name().trim().isEmpty()) {
            throw validation("权益名称不能为空。");
        }
        String name = command.name().trim();
        if (name.length() > 128) throw validation("权益名称不能超过 128 个字符。");
        if (enforceUniqueName) ensureUniqueActiveName(name, excludedId);
        String description = command.description() == null ? null : command.description().trim();
        if (description != null && description.isEmpty()) description = null;
        if (description != null && description.length() > 500) {
            throw validation("权益说明不能超过 500 个字符。");
        }
        return new DisplayValues(name, description, command.sortOrder() == null ? 100 : command.sortOrder());
    }

    private void ensureUniqueActiveName(String name, Long excludedId) {
        boolean duplicate = mapper.selectList(new QueryWrapper<CommercialEntitlementDefinitionEntity>()
                .eq("status", ACTIVE))
            .stream().anyMatch(item -> !item.id.equals(excludedId) && item.name.equalsIgnoreCase(name));
        if (duplicate) throw validation("启用的权益名称已存在。");
    }

    private String generateCode() {
        String code;
        do {
            code = "DISPLAY_" + UUID.randomUUID().toString().replace("-", "")
                .substring(0, 12).toUpperCase();
        } while (mapper.selectCount(new QueryWrapper<CommercialEntitlementDefinitionEntity>()
            .eq("code", code)) > 0);
        return code;
    }

    private CommercialEntitlementDefinitionResponse response(
        CommercialEntitlementDefinitionEntity entity
    ) {
        return new CommercialEntitlementDefinitionResponse(
            entity.id, entity.code, entity.name, entity.description, entity.category,
            entity.status, entity.sortOrder, entity.createdAt, entity.updatedAt);
    }

    private BusinessException validation(String message) {
        return new BusinessException(ErrorCode.VALIDATION_ERROR, message);
    }

    private record DisplayValues(String name, String description, Integer sortOrder) {}
}

record CommercialDisplayEntitlementCommand(String name, String description, Integer sortOrder) {}

record CommercialEntitlementDefinitionResponse(
    Long id,
    String code,
    String name,
    String description,
    String category,
    String status,
    Integer sortOrder,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}
