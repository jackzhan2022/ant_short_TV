package com.antshorttv.storage;

import java.time.Instant;

public record DeliveryGrant(String url, Instant expiresAt) {
}
