package com.antshorttv.commercial;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
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

public class PublishPackages {
    @Configuration(proxyBeanMethods=false)
    @EnableAutoConfiguration(excludeName={"org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration",
        "org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration",
        "org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration"})
    @MapperScan("com.antshorttv.commercial")
    @Import({CommercialPackageService.class, CommercialEntitlementCatalogService.class})
    static class Maintenance {
        @Bean ObjectMapper json() { return new ObjectMapper().findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS); }
    }
    record Offer(String code, String type, String name, String description, Integer months,
                 String price, String benefit, String points) {}
    static final List<Offer> OFFERS = List.of(
        new Offer("PKG-6E7F5F25A27B", "SUBSCRIPTION", "创作月卡",
            "适合持续创作。每个订阅月发放 6,000 积分，订阅周期 1 个月；生效时发放当期积分，续购顺延，无自动扣款。",
            1, "49.90", "PERIODIC_POINTS", "6000"),
        new Offer("ANTV-POINTS-1000", "POINT_PACKAGE", "1,000 积分包",
            "适合轻量体验和临时补充。支付成功后一次到账 1,000 积分，可用于文本、图片和视频创作，无自动续费。",
            null, "9.90", "ONE_TIME_POINTS", "1000"),
        new Offer("PKG-0ABA459F94DC", "POINT_PACKAGE", "5,000 积分包",
            "适合日常创作补充。支付成功后一次到账 5,000 积分，可用于文本、图片和视频创作，无自动续费。",
            null, "49.90", "ONE_TIME_POINTS", "5000"),
        new Offer("ANTV-POINTS-10000", "POINT_PACKAGE", "10,000 积分包",
            "适合批量创作补充。支付成功后一次到账 10,000 积分，可用于文本、图片和视频创作，无自动续费。",
            null, "99.00", "ONE_TIME_POINTS", "10000")
    );
    public static void main(String[] args) throws Exception {
        require(args.length==1 && List.of("check","apply","verify").contains(args[0]), "Expected check|apply|verify");
        SpringApplication app = new SpringApplication(Maintenance.class);
        app.setWebApplicationType(WebApplicationType.NONE);
        app.setDefaultProperties(Map.of("spring.datasource.url", env("MYSQL_URL"),
            "spring.datasource.username",env("MYSQL_USERNAME"),"spring.datasource.password",env("MYSQL_PASSWORD"),
            "spring.datasource.driver-class-name","com.mysql.cj.jdbc.Driver","spring.datasource.hikari.maximum-pool-size","2",
            "mybatis-plus.configuration.map-underscore-to-camel-case","true","spring.main.banner-mode","off",
            "logging.level.root","WARN","spring.flyway.enabled","false"));
        try (var context=app.run("--spring.config.location=optional:classpath:/maintenance-empty.properties")) {
            var service=context.getBean(CommercialPackageService.class);
            var jdbc=context.getBean(JdbcTemplate.class);
            var json=context.getBean(ObjectMapper.class);
            if (args[0].equals("verify")) {
                verify(service);
                System.out.println(json.writeValueAsString(service.listForSale(LocalDateTime.now())));
                return;
            }
            guard(jdbc,service);
            for (Offer offer:OFFERS) {
                BigDecimal cost=new BigDecimal(offer.points()).divide(new BigDecimal("210"),16,RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("1.20"));
                BigDecimal margin=new BigDecimal("0.994").subtract(cost.divide(new BigDecimal(offer.price()),16,RoundingMode.HALF_UP));
                require(margin.compareTo(new BigDecimal(".30"))>=0,"Margin below target");
                System.out.println(offer.name()+" price="+offer.price()+" points="+offer.points()+" benchmarkMargin="+margin);
            }
            if (args[0].equals("check")) { System.out.println("READ_ONLY_CHECK_PASSED"); return; }
            Path before=Path.of("packages-before.json"), receipt=Path.of("packages-receipt.json");
            require(!Files.exists(before)&&!Files.exists(receipt),"Previous attempt exists; inspect before repeating");
            json.writerWithDefaultPrettyPrinter().writeValue(before.toFile(),Map.of(
                "packages",jdbc.queryForList("select * from commercial_package order by id"),
                "versions",jdbc.queryForList("select * from commercial_package_version order by id"),
                "entitlements",jdbc.queryForList("select * from commercial_entitlement order by id")));
            new TransactionTemplate(context.getBean(PlatformTransactionManager.class)).execute(status->{
                jdbc.queryForList("select id from commercial_package order by id for update");
                jdbc.queryForList("select id from commercial_package_version order by id for update");
                guard(jdbc,service);
                LocalDateTime effective=LocalDateTime.now().withNano(0);
                for (Offer offer:OFFERS) {
                    var draft=service.createDraft(new CommercialPackageDraftCommand(offer.code(),offer.type(),offer.name(),offer.description(),
                        offer.months()==null?null:"MONTH",offer.months(),new BigDecimal(offer.price()),null,"CNY",effective,null,
                        List.of(new CommercialEntitlementInput(offer.benefit(),new BigDecimal(offer.points()))),null));
                    service.publish(draft.packageId(),draft.versionId(),null);
                }
                service.unpublish(5L,5L);
                verify(service);
                return null;
            });
            var catalog=service.listForSale(LocalDateTime.now());
            json.writerWithDefaultPrettyPrinter().writeValue(receipt.toFile(),catalog);
            System.out.println(json.writeValueAsString(catalog));
            System.out.println("PACKAGES_PUBLISHED_AND_VERIFIED");
        }
    }
    static void guard(JdbcTemplate jdbc, CommercialPackageService service) {
        require(jdbc.queryForObject("select count(*) from commercial_package",Integer.class)==7,"Package list changed");
        require(jdbc.queryForObject("select count(*) from commercial_package where (id=4 and code='PKG-6E7F5F25A27B' and package_type='SUBSCRIPTION') or (id=5 and code='PKG-0ABA459F94DC' and package_type='POINT_PACKAGE')",Integer.class)==2,"Existing package identities changed");
        require(jdbc.queryForObject("select count(*) from commercial_package_version",Integer.class)==7,"Version list changed");
        require(jdbc.queryForObject("select count(*) from commercial_package_version where id=4 and package_id=4 and status='OFF_SALE'",Integer.class)==1,"Monthly base changed");
        var catalog=service.listForSale(LocalDateTime.now());
        require(catalog.size()==1&&catalog.get(0).packageVersionId().equals(5L),"Sale catalog changed");
        require(catalog.get(0).price().compareTo(new BigDecimal("49.90"))==0,"Base price changed");
        require(jdbc.queryForObject("select count(*) from commercial_package_version where status='PUBLISHED'",Integer.class)==1,"Other live/future offers exist");
        require(jdbc.queryForObject("select count(*) from ai_model_point_price_version where id between 12 and 21 and status='PUBLISHED' and effective_from<=now() and (effective_to is null or effective_to>now())",Integer.class)==10,"Expected corrected model tariffs are not current");
        BigDecimal discount=jdbc.queryForObject("select coalesce(min(e.numeric_value),1) from team_subscription s join commercial_entitlement e on e.package_version_id=s.package_version_id where s.status in ('ACTIVE','QUEUED') and s.ends_at>now() and e.entitlement_type='GLOBAL_DISCOUNT'",BigDecimal.class);
        require(discount.compareTo(BigDecimal.ONE)>=0,"Subscription discount invalidates budget margin");
    }
    static void verify(CommercialPackageService service) {
        var catalog=service.listForSale(LocalDateTime.now());
        require(catalog.size()==4,"Expected exactly four sale offers");
        for(Offer expected:OFFERS) {
            var actual=catalog.stream().filter(item->item.code().equals(expected.code())).findFirst().orElseThrow();
            require(actual.name().equals(expected.name())&&actual.description().equals(expected.description()),"Name/description mismatch");
            require(actual.packageType().equals(expected.type())&&actual.price().compareTo(new BigDecimal(expected.price()))==0,"Price/type mismatch");
            require("CNY".equals(actual.currency())&&actual.listPrice()==null,"Unexpected currency/list price");
            require(java.util.Objects.equals(actual.periodMonths(),expected.months())
                &&java.util.Objects.equals(actual.billingPeriod(),expected.months()==null?null:"MONTH"),"Billing cycle mismatch");
            require(actual.entitlements().size()==1&&actual.entitlements().get(0).type().equals(expected.benefit())
                &&actual.entitlements().get(0).value().compareTo(new BigDecimal(expected.points()))==0,"Benefits mismatch");
        }
    }
    static String env(String name) { String value=System.getenv(name); require(value!=null&&!value.isBlank(),"Missing "+name);return value; }
    static void require(boolean condition,String message) {if(!condition)throw new IllegalStateException(message);}
}
