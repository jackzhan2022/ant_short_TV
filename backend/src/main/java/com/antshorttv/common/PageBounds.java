package com.antshorttv.common;

public record PageBounds(int current, int pageSize) {
    public static PageBounds of(Integer current, Integer pageSize) {
        return new PageBounds(current == null || current < 1 ? 1 : current,
            pageSize == null || pageSize < 1 ? 20 : Math.min(pageSize, 100));
    }

    public long offset() {
        return ((long) current - 1) * pageSize;
    }
}
