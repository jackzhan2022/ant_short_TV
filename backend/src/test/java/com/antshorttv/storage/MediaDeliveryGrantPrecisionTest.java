package com.antshorttv.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

class MediaDeliveryGrantPrecisionTest {
    @ParameterizedTest
    @ValueSource(ints = {0, 100, 499, 500, 750, 999})
    void firstUrlMatchesDatabaseReadbackAcrossThreeRequests(int milliseconds) throws Exception {
        Instant now = Instant.parse("2026-10-02T12:00:00Z").plusMillis(milliseconds);
        try (SecondsPrecisionStore store = new SecondsPrecisionStore()) {
            DeliveryGrant first = service(store, now).issue(request(false));
            DeliveryGrant second = service(store, now.plusSeconds(1)).issue(request(false));
            DeliveryGrant third = service(store, now.plusSeconds(2)).issue(request(false));

            assertThat(second.url()).isEqualTo(first.url());
            assertThat(third.url()).isEqualTo(first.url());
            assertThat(second.expiresAt()).isEqualTo(first.expiresAt());
            assertThat(store.inserts).isOne();
            assertThat(store.updates).isZero();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void renewalUrlMatchesPersistedReadbackForExpiredImagesAndExpiringVideos(boolean video) throws Exception {
        Instant now = Instant.parse("2026-10-02T12:00:00.750Z");
        DeliveryGrantRequest request = request(video);
        try (SecondsPrecisionStore store = new SecondsPrecisionStore()) {
            store.insert(MediaDeliveryGrantEntity.create(request,
                MediaDeliveryGrantService.objectKeyHash(request.objectKey()),
                now.plusSeconds(video ? 7199 : -1), 3, now.minusSeconds(604800)));

            DeliveryGrant renewed = service(store, now).issue(request);
            DeliveryGrant second = service(store, now.plusSeconds(1)).issue(request);
            DeliveryGrant third = service(store, now.plusSeconds(2)).issue(request);

            assertThat(second.url()).isEqualTo(renewed.url());
            assertThat(third.url()).isEqualTo(renewed.url());
            assertThat(second.expiresAt()).isEqualTo(renewed.expiresAt());
            assertThat(store.find(request.userId(), MediaDeliveryGrantService.objectKeyHash(request.objectKey())).revision)
                .isEqualTo(4);
            assertThat(store.updates).isOne();
        }
    }

    @Test
    void preservesExistingUnexpiredUrlWhenClockHasFractionalSeconds() throws Exception {
        Instant now = Instant.parse("2026-10-02T12:00:00.750Z");
        Instant expiry = Instant.parse("2026-10-08T18:15:31Z");
        DeliveryGrantRequest request = request(false);
        try (SecondsPrecisionStore store = new SecondsPrecisionStore()) {
            store.insert(MediaDeliveryGrantEntity.create(request,
                MediaDeliveryGrantService.objectKeyHash(request.objectKey()), expiry, 2,
                expiry.minusSeconds(604800)));

            DeliveryGrant grant = service(store, now).issue(request);

            assertThat(grant.url()).isEqualTo(new CdnTypeDSigner("https://cdn.example.com", "test-key")
                .sign(request.objectKey(), expiry));
            assertThat(grant.expiresAt()).isEqualTo(expiry);
            assertThat(store.updates).isZero();
        }
    }

    private MediaDeliveryGrantService service(SecondsPrecisionStore store, Instant now) {
        ObjectStorageProperties properties = new ObjectStorageProperties();
        properties.setCdnDomain("https://cdn.example.com");
        properties.setCdnTypeDKey("test-key");
        return new MediaDeliveryGrantService(store, properties, Clock.fixed(now, ZoneOffset.UTC));
    }

    private DeliveryGrantRequest request(boolean video) {
        return new DeliveryGrantRequest(11L, 22L, 33L,
            video ? "AI_VIDEO_RESULT" : "AI_IMAGE_RESULT", 44L, "v1",
            video ? "VIDEO" : "THUMBNAIL",
            "materials/11/22/images/202610/44/v1/derived/display.png", video);
    }

    private static class SecondsPrecisionStore extends MediaDeliveryGrantStore implements AutoCloseable {
        private final Connection connection;
        private final JdbcTemplate jdbc;
        private int inserts;
        private int updates;

        SecondsPrecisionStore() throws Exception {
            connection = DriverManager.getConnection("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL", "sa", "");
            jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
            // DATETIME(0) matches the deployed MySQL precision, including fractional-second rounding.
            jdbc.execute("""
                create table grant_expiry (
                    user_id bigint, object_key_hash char(64), expires_at datetime(0), revision int,
                    created_at datetime(0), updated_at datetime(0), primary key(user_id, object_key_hash)
                )
                """);
        }

        @Override
        public MediaDeliveryGrantEntity find(Long userId, String objectKeyHash) {
            var rows = jdbc.query("select * from grant_expiry where user_id=? and object_key_hash=?", (rs, index) -> {
                MediaDeliveryGrantEntity entity = new MediaDeliveryGrantEntity();
                entity.userId = rs.getLong("user_id");
                entity.objectKeyHash = rs.getString("object_key_hash");
                entity.expiresAt = rs.getObject("expires_at", LocalDateTime.class);
                entity.revision = rs.getInt("revision");
                entity.createdAt = rs.getObject("created_at", LocalDateTime.class);
                entity.updatedAt = rs.getObject("updated_at", LocalDateTime.class);
                return entity;
            }, userId, objectKeyHash);
            return rows.isEmpty() ? null : rows.get(0);
        }

        @Override
        public void insert(MediaDeliveryGrantEntity entity) {
            jdbc.update("insert into grant_expiry values(?,?,?,?,?,?)", entity.userId, entity.objectKeyHash,
                entity.expiresAt, entity.revision, entity.createdAt, entity.updatedAt);
            inserts++;
        }

        @Override
        public void update(MediaDeliveryGrantEntity entity) {
            jdbc.update("update grant_expiry set expires_at=?, revision=?, updated_at=? where user_id=? and object_key_hash=?",
                entity.expiresAt, entity.revision, entity.updatedAt, entity.userId, entity.objectKeyHash);
            updates++;
        }

        @Override
        public void close() throws Exception {
            connection.close();
        }
    }
}
