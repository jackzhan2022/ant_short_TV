package com.antshorttv.workflowagent.skill;

import static org.assertj.core.api.Assertions.assertThat;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AssetCatalogSkillUpgradeTest {
    @TempDir Path root;
    @Test void preservesCustomContentAndIsIdempotent() {
        var repository = new FileSkillRepository(root, 100_000, new SkillDocumentParser());
        String original = "---\nname: Custom extraction\ndescription: Custom rules\n---\n用户保留规则。\n";
        repository.create(AssetCatalogSkillUpgrade.CODE, original);
        var upgrade = new AssetCatalogSkillUpgrade(repository);
        upgrade.run(null);
        var updated = repository.get(AssetCatalogSkillUpgrade.CODE);
        assertThat(updated.content()).startsWith(original).contains("search_script_assets", "read_asset_details");
        upgrade.run(null);
        assertThat(repository.get(AssetCatalogSkillUpgrade.CODE).revision()).isEqualTo(updated.revision());
    }
    @Test void upgradesAlreadyInstalledV1ProtocolToBatchLookupWithoutReplacingCustomRules() {
        var repository = new FileSkillRepository(root, 100_000, new SkillDocumentParser());
        String original = "---\nname: Custom extraction\ndescription: Custom rules\n---\n用户保留规则。\n"
            + "<!-- asset-catalog-protocol:v1 -->\n旧检索协议。\n";
        repository.create(AssetCatalogSkillUpgrade.CODE, original);
        var upgrade = new AssetCatalogSkillUpgrade(repository);
        upgrade.run(null);
        var updated = repository.get(AssetCatalogSkillUpgrade.CODE);
        assertThat(updated.content()).startsWith(original)
            .contains("asset-catalog-protocol:v2", "queries", "批量", "同一轮", "已有可信候选");
        upgrade.run(null);
        assertThat(repository.get(AssetCatalogSkillUpgrade.CODE).revision()).isEqualTo(updated.revision());
    }
}
