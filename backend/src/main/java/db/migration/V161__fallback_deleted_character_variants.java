package db.migration;

import com.antshorttv.script.CharacterVariantReferenceFallback;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

/** Repairs existing editable references; never rewrites past generation requests or results. */
public class V161__fallback_deleted_character_variants extends BaseJavaMigration {
    @Override
    public void migrate(Context context) {
        CharacterVariantReferenceFallback.repairHistorical(new JdbcTemplate(
            new SingleConnectionDataSource(context.getConnection(),true)));
    }
}
