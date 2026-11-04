package com.antshorttv.storage;

import com.antshorttv.style.AssetArtStyleImageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qcloud.cos.COS;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;

/** Explicit maintenance entry point: no application scan, migrations or business schedulers. */
public class ImportArtStyleImages {
    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration(excludeName = {
        "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration",
        "org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration",
        "org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration"
    })
    @EnableConfigurationProperties(ObjectStorageProperties.class)
    @MapperScan(basePackages = "com.antshorttv.storage", annotationClass = Mapper.class)
    @Import({TencentCosConfiguration.class, CosStorageMetrics.class, ObjectStorageKeyFactory.class,
        ObjectStorageService.class, MyBatisMediaObjectStore.class, MyBatisMediaProcessingJobStore.class,
        MyBatisMediaDeliveryGrantStore.class, MediaObjectRegistry.class, MediaProcessingJobCoordinator.class,
        ImageDisplayRenditionPlanner.class, CloudInfiniteProcessingService.class,
        ImageDisplayRenditionService.class, MediaDeliveryGrantService.class, AssetArtStyleImageService.class})
    static class Maintenance {
        @Bean ObjectMapper objectMapper() { return new ObjectMapper(); }
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 3 || !List.of("check", "apply", "verify").contains(args[1]))
            throw new IllegalArgumentException("Usage: image-directory check|apply|verify receipt.json");
        Path directory = Path.of(args[0]);
        // Validate the complete set before connecting to the database or uploading anything.
        var manifest = new AssetArtStyleImageService(null, null, null, null, new ObjectMapper());
        for (var sample : manifest.samples()) manifest.validateSource(sample.code(), Files.readAllBytes(directory.resolve(sample.code() + ".png")));
        System.out.println("Validated all 11 reviewed source hashes.");
        if (args[1].equals("check")) return;

        var app = new SpringApplication(Maintenance.class);
        app.setWebApplicationType(WebApplicationType.NONE);
        Map<String, Object> config = new HashMap<>();
        config.put("spring.datasource.url", required("MYSQL_URL"));
        config.put("spring.datasource.username", required("MYSQL_USERNAME"));
        config.put("spring.datasource.password", required("MYSQL_PASSWORD"));
        config.put("spring.datasource.driver-class-name", "com.mysql.cj.jdbc.Driver");
        config.put("spring.datasource.hikari.maximum-pool-size", "2");
        config.put("mybatis-plus.configuration.map-underscore-to-camel-case", "true");
        config.put("spring.main.banner-mode", "off");
        config.put("logging.level.root", "WARN");
        config.put("spring.flyway.enabled", "false");
        for (var key : List.of("BUCKET", "REGION", "CLASS", "CDN_DOMAIN", "CDN_TYPE_D_KEY", "CI_CALLBACK_URL")) {
            String value = System.getenv("OBJECT_STORAGE_" + key);
            if (value != null) config.put("object-storage." + (key.equals("CLASS") ? "storage-class" : key.toLowerCase(Locale.ROOT).replace('_', '-')), value);
        }
        app.setDefaultProperties(config);
        try (var context = app.run("--spring.config.location=optional:classpath:/art-style-maintenance-empty.properties")) {
            var images = context.getBean(AssetArtStyleImageService.class);
            var renditions = context.getBean(ImageDisplayRenditionService.class);
            var cos = context.getBean(COS.class);
            var props = context.getBean(ObjectStorageProperties.class);
            var jdbc = context.getBean(JdbcTemplate.class);
            long legacyBefore = jdbc.queryForObject("select count(*) from style_library", Long.class);
            var jobsBefore = jobSnapshot(jdbc);
            boolean previouslyReady = images.samples().stream().allMatch(s -> {
                var d = renditions.displayDetails(images.identity(s.code()));
                return d != null && "READY".equals(d.status());
            });
            if (args[1].equals("apply")) {
                for (var sample : images.samples()) {
                    images.importImage(sample.code(), Files.readAllBytes(directory.resolve(sample.code() + ".png")));
                    System.out.println("Imported/reused " + sample.code());
                }
            }
            var http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
            var receipt = new ArrayList<Map<String, Object>>();
            boolean allReady = true;
            for (var sample : images.samples()) {
                var display = renditions.displayDetails(images.identity(sample.code()));
                if (display == null || !"READY".equals(display.status())) {
                    System.out.println(sample.code() + " " + (display == null ? "MISSING" : display.status()));
                    allReady = false;
                    continue;
                }
                var head = cos.getObjectMetadata(props.getBucket(), display.objectKey());
                if (head.getContentLength() != display.fileSize() || !"image/png".equals(head.getContentType()))
                    throw new IllegalStateException("COS metadata mismatch: " + sample.code());
                // Exercise the same service as the stable HTTP redirect, without recording signed URLs.
                var response = http.send(HttpRequest.newBuilder(URI.create(images.deliveryUrl(sample.code())))
                    .header("Referer", "https://antv.aixmax.cn/")
                    .timeout(Duration.ofSeconds(30)).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
                var decoded = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(response.body()));
                if (response.statusCode() != 200 || decoded == null || response.body().length != display.fileSize())
                    throw new IllegalStateException("CDN verification failed: " + sample.code() + " status=" + response.statusCode());
                var original = renditions.originalDetails(images.identity(sample.code()));
                receipt.add(Map.of("code", sample.code(), "imageUrl", images.imageUrl(sample.code()),
                    "objectKey", display.objectKey(), "originalBytes", original.fileSize(),
                    "displayBytes", display.fileSize(), "cdnStatus", response.statusCode(),
                    "width", decoded.getWidth(), "height", decoded.getHeight(), "status", display.status()));
                System.out.println(sample.code() + " READY COS verified CDN 200 " + original.fileSize() + " -> " + display.fileSize());
            }
            if (jdbc.queryForObject("select count(*) from style_library", Long.class) != legacyBefore)
                throw new IllegalStateException("Legacy style count changed during maintenance");
            if (previouslyReady && !jobsBefore.equals(jobSnapshot(jdbc)))
                throw new IllegalStateException("Ready import unexpectedly changed compression jobs");
            new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(Path.of(args[2]).toFile(), receipt);
            if (!allReady) throw new IllegalStateException("Compression pending; rerun verify after callbacks complete.");
            System.out.println("Verified 11 displays; legacy style rows unchanged: " + legacyBefore);
            if (previouslyReady) System.out.println("Existing compression job IDs and attempt counts unchanged.");
        }
    }

    private static List<Map<String, Object>> jobSnapshot(JdbcTemplate jdbc) {
        return jdbc.queryForList("select j.id, j.attempt_no, j.status from media_processing_job j join media_object m on m.id=j.media_object_id where m.asset_type='STYLE_LIBRARY_V2' order by j.id");
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException("Missing " + name);
        return value;
    }
}
