package com.antshorttv.common;

import java.util.List;
import java.util.function.Function;

public record MediaPage<T>(List<T> data, int current, int pageSize, long total) {
    public <R> MediaPage<R> map(Function<T, R> mapper) {
        return new MediaPage<>(data.stream().map(mapper).toList(), current, pageSize, total);
    }
}
