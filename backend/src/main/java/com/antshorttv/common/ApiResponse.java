package com.antshorttv.common;

public record ApiResponse<T>(
    boolean success,
    T data,
    String errorCode,
    String errorMessage
) {

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null, null);
    }

    public static ApiResponse<Void> ok() {
        return new ApiResponse<>(true, null, null, null);
    }

    public static <T> ApiResponse<T> fail(String errorCode, String errorMessage) {
        return new ApiResponse<>(false, null, errorCode, errorMessage);
    }
}
