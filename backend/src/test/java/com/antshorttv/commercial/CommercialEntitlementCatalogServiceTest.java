package com.antshorttv.commercial;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.antshorttv.common.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class CommercialEntitlementCatalogServiceTest {
    @Autowired CommercialEntitlementCatalogService service;

    @Test
    void listsSystemAndDisplayEntitlementsInStableOrder() {
        CommercialEntitlementDefinitionResponse created = service.create(
            new CommercialDisplayEntitlementCommand("使用全部模型", "商品介绍文案", 5));

        assertThat(created.code()).matches("DISPLAY_[A-Z0-9]{12}");
        assertThat(created.name()).isEqualTo("使用全部模型");
        assertThat(created.category()).isEqualTo("DISPLAY");
        assertThat(created.status()).isEqualTo("ACTIVE");
        assertThat(service.list()).extracting(CommercialEntitlementDefinitionResponse::code)
            .containsExactly(created.code(), "ONE_TIME_POINTS", "PERIODIC_POINTS", "GLOBAL_DISCOUNT");
    }

    @Test
    void trimsUpdatesDisablesAndEnablesDisplayEntitlementWithoutChangingCode() {
        CommercialEntitlementDefinitionResponse created = service.create(
            new CommercialDisplayEntitlementCommand("  使用全部模型  ", "  覆盖全部模型  ", 40));

        CommercialEntitlementDefinitionResponse updated = service.update(created.id(),
            new CommercialDisplayEntitlementCommand("  使用所有模型  ", "  新说明  ", 45));
        assertThat(updated.code()).isEqualTo(created.code());
        assertThat(updated.name()).isEqualTo("使用所有模型");
        assertThat(updated.description()).isEqualTo("新说明");
        assertThat(updated.sortOrder()).isEqualTo(45);

        assertThat(service.disable(created.id()).status()).isEqualTo("INACTIVE");
        assertThat(service.enable(created.id()).status()).isEqualTo("ACTIVE");
    }

    @Test
    void rejectsBlankAndDuplicateActiveDisplayNames() {
        service.create(new CommercialDisplayEntitlementCommand("使用全部模型", null, null));

        assertThatThrownBy(() -> service.create(
            new CommercialDisplayEntitlementCommand(" 使用全部模型 ", null, 10)))
            .isInstanceOf(BusinessException.class).hasMessageContaining("已存在");
        assertThatThrownBy(() -> service.create(
            new CommercialDisplayEntitlementCommand("  ", null, 10)))
            .isInstanceOf(BusinessException.class).hasMessageContaining("名称");
    }

    @Test
    void rejectsValuesThatExceedDatabaseLengths() {
        assertThatThrownBy(() -> service.create(
            new CommercialDisplayEntitlementCommand("权".repeat(129), null, 10)))
            .isInstanceOf(BusinessException.class).hasMessageContaining("128");
        assertThatThrownBy(() -> service.create(
            new CommercialDisplayEntitlementCommand("有效名称", "说".repeat(501), 10)))
            .isInstanceOf(BusinessException.class).hasMessageContaining("500");
    }

    @Test
    void allowsInactiveNamesToBeStagedButRechecksWhenEnabled() {
        CommercialEntitlementDefinitionResponse active = service.create(
            new CommercialDisplayEntitlementCommand("使用全部模型", null, 10));
        CommercialEntitlementDefinitionResponse inactive = service.create(
            new CommercialDisplayEntitlementCommand("优先体验", null, 20));
        service.disable(inactive.id());

        CommercialEntitlementDefinitionResponse staged = service.update(inactive.id(),
            new CommercialDisplayEntitlementCommand(active.name(), null, 20));

        assertThat(staged.status()).isEqualTo("INACTIVE");
        assertThat(staged.name()).isEqualTo(active.name());
        assertThatThrownBy(() -> service.enable(staged.id()))
            .isInstanceOf(BusinessException.class).hasMessageContaining("已存在");
    }

    @Test
    void rejectsSystemEntitlementMutation() {
        CommercialEntitlementDefinitionResponse system = service.list().stream()
            .filter(item -> "ONE_TIME_POINTS".equals(item.code()))
            .findFirst().orElseThrow();

        assertThatThrownBy(() -> service.update(system.id(),
            new CommercialDisplayEntitlementCommand("改名", null, 1)))
            .isInstanceOf(BusinessException.class).hasMessageContaining("系统权益");
        assertThatThrownBy(() -> service.disable(system.id()))
            .isInstanceOf(BusinessException.class).hasMessageContaining("系统权益");
    }
}
