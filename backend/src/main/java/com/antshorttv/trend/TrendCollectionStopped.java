package com.antshorttv.trend;

final class TrendCollectionStopped extends RuntimeException {
    final String status;
    final String code;
    TrendCollectionStopped(String status, String code, String message) {
        super(message); this.status=status; this.code=code;
    }
}
