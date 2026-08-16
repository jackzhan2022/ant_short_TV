package com.antshorttv.security;

import com.antshorttv.common.BusinessException;
import com.antshorttv.common.ErrorCode;
import java.util.Optional;

public final class CurrentUserHolder {

    private static final ThreadLocal<CurrentUser> CURRENT_USER = new ThreadLocal<>();

    private CurrentUserHolder() {
    }

    public static void set(CurrentUser currentUser) {
        CURRENT_USER.set(currentUser);
    }

    public static Optional<CurrentUser> get() {
        return Optional.ofNullable(CURRENT_USER.get());
    }

    public static CurrentUser require() {
        return get().orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED, "请先登录。"));
    }

    public static void clear() {
        CURRENT_USER.remove();
    }
}
