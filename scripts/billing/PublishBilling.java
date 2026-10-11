package com.antshorttv.accounting;

import com.antshorttv.commercial.CommercialEntitlementCatalogService;
import com.antshorttv.commercial.CommercialPackageService;
import com.antshorttv.points.AiPointSettlementService;
import com.antshorttv.points.PointAccountingService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Explicit maintenance entry point; no application component scan or business schedulers. */
public class PublishBilling {
    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration(excludeName = {
        "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration",
        "org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration",
        "org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration"
    })
    @MapperScan({"com.antshorttv.accounting", "com.antshorttv.points", "com.antshorttv.commercial"})
    @Import({AiModelPriceService.class, AiModelPointPriceService.class,
        AiModelPriceVersionAllocator.class, AiModelBillingResolver.class,
        AiPointSettlementService.class, PointAccountingService.class,
        CommercialPackageService.class, CommercialEntitlementCatalogService.class})
    static class Maintenance {
        @Bean ObjectMapper objectMapper() {
            return new ObjectMapper().findAndRegisterModules().disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 3 || !List.of("check", "apply", "verify", "rollback").contains(args[1])) {
            throw new IllegalArgumentException("Usage: PublishBilling.java plan.json check|apply|verify|rollback receipt.json");
        }
        ObjectMapper json = new ObjectMapper().findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        JsonNode plan = json.readTree(Files.readString(Path.of(args[0])));
        Path receiptPath = Path.of(args[2]);
        SpringApplication app = new SpringApplication(Maintenance.class);
        app.setWebApplicationType(WebApplicationType.NONE);
        app.setDefaultProperties(Map.of(
            "spring.datasource.url", requireEnv("MYSQL_URL"),
            "spring.datasource.username", requireEnv("MYSQL_USERNAME"),
            "spring.datasource.password", requireEnv("MYSQL_PASSWORD"),
            "spring.datasource.driver-class-name", "com.mysql.cj.jdbc.Driver",
            "spring.datasource.hikari.maximum-pool-size", "2",
            "mybatis-plus.configuration.map-underscore-to-camel-case", "true",
            "spring.main.banner-mode", "off", "logging.level.root", "WARN",
            "spring.flyway.enabled", "false"));
        try (var context = app.run("--spring.config.location=optional:classpath:/billing-maintenance-empty.properties")) {
            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
            if ("rollback".equals(args[1])) {
                requireServiceInactive();
                JsonNode receipt = json.readTree(Files.readString(receiptPath));
                JsonNode before = json.readTree(Files.readString(Path.of(receipt.path("beforeSnapshot").asText())));
                require(receipt.path("operation").asText().equals(plan.path("operation").asText()), "Receipt operation mismatch");
                new TransactionTemplate(context.getBean(PlatformTransactionManager.class)).execute(status -> {
                    for (JsonNode published : receipt.path("published")) {
                        long modelId = published.path("modelId").asLong();
                        long costId = published.path("costVersionId").asLong();
                        long pointId = published.path("pointVersionId").asLong();
                        require(jdbc.queryForObject("select count(*) from ai_execution_task where cost_price_version_id=? or point_price_version_id=?",
                            Long.class, costId, pointId) == 0, "New prices already referenced; automatic rollback is unsafe");
                        JsonNode original = null;
                        for (JsonNode candidate : before) if (candidate.path("modelId").asLong() == modelId) original = candidate;
                        require(original != null, "Missing before snapshot");
                        require(jdbc.update("update ai_model_price_version set status='REVOKED' where id=? and model_id=? and status='PUBLISHED'", costId, modelId) == 1,
                            "Cost version changed during rollback");
                        require(jdbc.update("update ai_model_point_price_version set status='REVOKED' where id=? and model_id=? and status='PUBLISHED'", pointId, modelId) == 1,
                            "Point version changed during rollback");
                        for (String kind : List.of("cost", "point")) {
                            JsonNode old = original.get(kind);
                            if (old != null && !old.isNull()) {
                                LocalDateTime end = old.path("effectiveTo").isNull() ? null : LocalDateTime.parse(old.path("effectiveTo").asText());
                                String table = "cost".equals(kind) ? "ai_model_price_version" : "ai_model_point_price_version";
                                require(jdbc.update("update " + table + " set effective_to=? where id=? and model_id=? and status='PUBLISHED'",
                                    end, old.path("id").asLong(), modelId) == 1, "Previous version changed during rollback");
                            }
                        }
                    }
                    for (JsonNode version : receipt.path("testVersionsOffSale")) {
                        require(jdbc.update("update commercial_package_version set status='PUBLISHED' where id=? and package_id=? and status='OFF_SALE' and price=?",
                            version.path("versionId").asLong(), version.path("packageId").asLong(),
                            new BigDecimal(version.path("expectedPrice").asText())) == 1, "Test package changed during rollback");
                    }
                    return null;
                });
                System.out.println("Previous price activation windows restored; new price components retained as revoked history.");
                return;
            }
            var costs = context.getBean(AiModelPriceService.class);
            var points = context.getBean(AiModelPointPriceService.class);
            var costVersions = context.getBean(AiModelPriceVersionMapper.class);
            var pointVersions = context.getBean(AiModelPointPriceVersionMapper.class);
            var billing = context.getBean(AiModelBillingResolver.class);
            var calculator = context.getBean(AiPointSettlementService.class);
            if ("verify".equals(args[1])) {
                verify(plan, billing, calculator, LocalDateTime.now());
                System.out.println("Verified all current model billing scenarios.");
                return;
            }
            LocalDateTime now = LocalDateTime.now();
            List<Map<String, Object>> before = new ArrayList<>();
            for (JsonNode model : plan.path("models")) {
                long id = model.path("id").asLong();
                String code = jdbc.queryForObject("select code from ai_model where id=? and status='ENABLED'", String.class, id);
                require(model.path("code").asText().equals(code), "Model identity changed: " + id);
                var cost = costVersions.selectEffective(id, now);
                var point = pointVersions.selectEffective(id, now);
                require(java.util.Objects.equals(cost == null ? null : cost.id, nullableLong(model.get("expectedCostVersion"))),
                    "Current cost version changed: " + id);
                require(java.util.Objects.equals(point == null ? null : point.id, nullableLong(model.get("expectedPointVersion"))),
                    "Current point version changed: " + id);
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("modelId", id);
                item.put("cost", cost);
                item.put("point", point);
                item.put("costComponents", cost == null ? List.of() : context.getBean(AiModelPriceComponentMapper.class).selectByVersion(cost.id));
                item.put("pointComponents", point == null ? List.of() : points.components(point.id));
                before.add(item);
                for (JsonNode row : model.path("components")) {
                    AiUsageMetric.valueOf(row.path("metric").asText());
                    require(row.path("unitSize").decimalValue().signum() > 0, "Invalid unit");
                    require(new BigDecimal(row.path("unitPrice").asText()).multiply(new BigDecimal("210"))
                        .compareTo(new BigDecimal(row.path("pointRate").asText())) == 0, "Price multiplier mismatch");
                }
            }
            require(jdbc.queryForObject("select count(*) from ai_execution_task where status in ('PENDING','QUEUED','RUNNING')", Long.class) == 0,
                "Active AI executions; do not apply");
            BigDecimal activeDiscount = jdbc.queryForObject("""
                select coalesce(min(e.numeric_value),1) from team_subscription s
                join commercial_entitlement e on e.package_version_id=s.package_version_id
                where s.status in ('ACTIVE','QUEUED') and s.ends_at>now() and e.entitlement_type='GLOBAL_DISCOUNT'
                """, BigDecimal.class);
            require(activeDiscount.compareTo(BigDecimal.ONE) >= 0, "Existing subscription discount invalidates margin assumption");
            BigDecimal offeredDiscount = jdbc.queryForObject("""
                select coalesce(min(e.numeric_value),1) from commercial_package_version v
                join commercial_entitlement e on e.package_version_id=v.id
                where v.status='PUBLISHED' and (v.effective_to is null or v.effective_to>now())
                  and e.entitlement_type='GLOBAL_DISCOUNT'
                """, BigDecimal.class);
            require(offeredDiscount.compareTo(BigDecimal.ONE) >= 0, "Offered discount invalidates margin assumption");
            for (Map<String, Object> offer : jdbc.queryForList("""
                select v.id,v.price,sum(case when e.entitlement_type='ONE_TIME_POINTS' then e.numeric_value
                    when e.entitlement_type='PERIODIC_POINTS' then e.numeric_value*coalesce(v.period_months,1) else 0 end) points
                  from commercial_package_version v join commercial_entitlement e on e.package_version_id=v.id
                 where v.status='PUBLISHED' and (v.effective_to is null or v.effective_to>now())
                   and v.id not in (6,7) group by v.id
                """)) {
                BigDecimal granted = new BigDecimal(offer.get("points").toString());
                BigDecimal revenue = new BigDecimal(offer.get("price").toString());
                require(granted.signum() == 0 || revenue.divide(granted, 16, java.math.RoundingMode.DOWN)
                    .compareTo(new BigDecimal(plan.path("minimumCnyPerPoint").asText())) >= 0,
                    "Published package price invalidates margin floor: " + offer.get("id"));
            }
            if ("check".equals(args[1])) {
                System.out.println("Identity, current versions, price arithmetic and idle-execution checks passed for " + before.size() + " models.");
                return;
            }
            requireServiceInactive();
            require(!Files.exists(receiptPath), "Receipt already exists; do not repeat apply");
            Path beforePath = receiptPath.resolveSibling(receiptPath.getFileName() + ".before.json");
            if (Files.exists(beforePath)) {
                require(json.valueToTree(before).equals(json.readTree(Files.readString(beforePath))),
                    "Current prices differ from preserved before snapshot; inspect failed application before retrying");
            } else {
                json.writerWithDefaultPrettyPrinter().writeValue(beforePath.toFile(), before);
            }
            List<Map<String, Object>> published = new ArrayList<>();
            LocalDateTime effective = LocalDateTime.now().withNano(0);
            new TransactionTemplate(context.getBean(PlatformTransactionManager.class)).execute(status -> {
                for (JsonNode model : plan.path("models")) {
                    long id = model.path("id").asLong();
                    List<AiModelPriceComponentEntity> costRows = new ArrayList<>();
                    List<AiModelPointPriceComponentEntity> pointRows = new ArrayList<>();
                    for (JsonNode row : model.path("components")) {
                        Map<String, String> dimensions = new LinkedHashMap<>();
                        row.path("dimensions").fields().forEachRemaining(e -> dimensions.put(e.getKey(), e.getValue().asText()));
                        var c = new AiModelPriceComponentEntity();
                        c.metric = row.path("metric").asText(); c.unitSize = row.path("unitSize").decimalValue();
                        c.unitPrice = new BigDecimal(row.path("unitPrice").asText()); c.currency = "CNY";
                        c.dimensionsJson = AiAccountingJson.write(dimensions); c.dimensionsKey = AiAccountingJson.canonicalKey(dimensions);
                        costRows.add(c);
                        var p = new AiModelPointPriceComponentEntity();
                        p.metric = c.metric; p.unitSize = c.unitSize; p.pointRate = new BigDecimal(row.path("pointRate").asText());
                        p.dimensions = dimensions; pointRows.add(p);
                    }
                    var v = new AiModelPriceVersionEntity(); v.modelId = id; v.effectiveFrom = effective;
                    costs.publish(v, costRows);
                    var p = points.publish(id, effective, null, pointRows, null);
                    published.add(Map.of("modelId", id, "costVersionId", v.id, "pointVersionId", p.id,
                        "costVersion", v.versionNo, "pointVersion", p.versionNo));
                }
                for (JsonNode offSale : plan.path("unpublishTestVersions")) {
                    long id = offSale.path("versionId").asLong();
                    Map<String, Object> old = jdbc.queryForMap("select package_id,price,status from commercial_package_version where id=? for update", id);
                    require(((Number)old.get("package_id")).longValue() == offSale.path("packageId").asLong()
                        && new BigDecimal(old.get("price").toString()).compareTo(new BigDecimal(offSale.path("expectedPrice").asText())) == 0
                        && "PUBLISHED".equals(old.get("status")), "Test package changed: " + id);
                    context.getBean(CommercialPackageService.class).unpublish(offSale.path("packageId").asLong(), id);
                }
                verify(plan, billing, calculator, effective);
                return null;
            });
            json.writerWithDefaultPrettyPrinter().writeValue(receiptPath.toFile(), Map.of(
                "operation", plan.path("operation").asText(), "effectiveFrom", effective,
                "published", published, "testVersionsOffSale", plan.path("unpublishTestVersions"),
                "beforeSnapshot", beforePath.toString()));
            System.out.println(json.writeValueAsString(Map.of("published", published, "effectiveFrom", effective, "receipt", receiptPath.toString())));
        }
    }

    private static void verify(JsonNode plan, AiModelBillingResolver billing,
        AiPointSettlementService calculator, LocalDateTime at) {
        for (JsonNode model : plan.path("models")) {
            List<Map<String, String>> cases = new ArrayList<>(); cases.add(Map.of());
            for (JsonNode row : model.path("components")) {
                if (!row.path("dimensions").isEmpty()) {
                    Map<String, String> dimensions = new LinkedHashMap<>();
                    row.path("dimensions").fields().forEachRemaining(e -> dimensions.put(e.getKey(), e.getValue().asText()));
                    cases.add(dimensions);
                }
            }
            for (Map<String, String> dimensions : cases) {
                Map<AiUsageMetric, BigDecimal> usage = new EnumMap<>(AiUsageMetric.class);
                for (JsonNode row : model.path("components")) usage.put(AiUsageMetric.valueOf(row.path("metric").asText()), BigDecimal.ONE);
                ModelBillingSnapshot resolved = billing.requireComplete(model.path("id").asLong(), usage.keySet(), dimensions, at);
                for (AiUsageMetric metric : usage.keySet()) {
                    JsonNode expected = null;
                    for (JsonNode row : model.path("components")) {
                        if (!metric.name().equals(row.path("metric").asText())) continue;
                        boolean matches = true;
                        var fields = row.path("dimensions").fields();
                        while (fields.hasNext()) {
                            var entry = fields.next();
                            if (!entry.getValue().asText().equals(dimensions.get(entry.getKey()))) matches = false;
                        }
                        if (matches && (expected == null || row.path("dimensions").size() > expected.path("dimensions").size())) expected = row;
                    }
                    require(expected != null, "Plan has no matching metric: " + metric);
                    var actual = resolved.costComponents().get(metric);
                    BigDecimal unit = expected.path("unitSize").decimalValue();
                    require(actual != null && "CNY".equals(actual.currency)
                        && actual.unitSize.compareTo(unit) == 0
                        && actual.unitPrice.compareTo(new BigDecimal(expected.path("unitPrice").asText())) == 0,
                        "Cost differs from reviewed plan: " + model.path("id") + " " + metric + " " + dimensions);
                    BigDecimal expectedPoints = new BigDecimal(expected.path("pointRate").asText())
                        .divide(unit, 8, java.math.RoundingMode.HALF_UP);
                    require(calculator.calculateModelPoints(resolved.pointVersionId(), Map.of(metric, BigDecimal.ONE), dimensions)
                        .compareTo(expectedPoints) == 0,
                        "Points differ from reviewed plan: " + model.path("id") + " " + metric + " " + dimensions);
                }
                BigDecimal cost = resolved.costComponents().values().stream()
                    .map(c -> c.unitPrice.divide(c.unitSize, 12, java.math.RoundingMode.HALF_UP))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
                BigDecimal charged = calculator.calculateModelPoints(resolved.pointVersionId(), usage, dimensions);
                require(charged.subtract(cost.multiply(new BigDecimal("210"))).abs().compareTo(new BigDecimal("0.000001")) <= 0,
                    "Cost/point calculation disagrees: " + model.path("id") + " " + dimensions);
                require(charged.signum() > 0, "Model has no chargeable benefit");
            }
        }
    }

    private static void requireServiceInactive() throws Exception {
        Process state = new ProcessBuilder("systemctl", "is-active", "antv.service").start();
        String value = new String(state.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).trim();
        state.waitFor();
        require("inactive".equals(value), "Stop antv.service before atomic code/price cutover");
    }

    private static String requireEnv(String key) {
        String value = System.getenv(key);
        require(value != null && !value.isBlank(), "Missing environment key: " + key);
        return value;
    }
    private static Long nullableLong(JsonNode value) { return value == null || value.isNull() ? null : value.asLong(); }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
