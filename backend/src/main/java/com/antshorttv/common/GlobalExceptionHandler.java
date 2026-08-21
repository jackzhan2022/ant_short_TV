package com.antshorttv.common;

import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException exception) {
        HttpStatus status = switch (exception.getErrorCode()) {
            case UNAUTHORIZED, INVALID_CREDENTIALS -> HttpStatus.UNAUTHORIZED;
            case FORBIDDEN, TENANT_DISABLED, MEMBER_REMOVED, OWNER_LEAVE_BLOCKED,
                OWNER_ROLE_IMMUTABLE, OWNER_ROLE_DELETE_BLOCKED, PROJECT_ACCESS_DENIED -> HttpStatus.FORBIDDEN;
            case DUPLICATE_MOBILE, DUPLICATE_PENDING_INVITATION, ALREADY_TENANT_MEMBER,
                ROLE_NAME_DUPLICATE, ROLE_DISABLED, ROLE_IN_USE, ORGANIZATION_HAS_CHILDREN,
                ORGANIZATION_HAS_MEMBERS, PROJECT_MEMBER_EXISTS, AI_IMAGE_RESULT_IN_USE,
                AI_VIDEO_RESULT_IN_USE, AI_VIDEO_CONCURRENCY_LIMIT_EXCEEDED,
                SCRIPT_VERSION_CONFLICT, AI_VOICE_RESULT_IN_USE, STORYBOARD_SUBTITLE_IN_USE,
                SHOT_COMPOSE_RESULT_IN_USE, EPISODE_VIDEO_VERSION_IN_USE -> HttpStatus.CONFLICT;
            case NOT_FOUND, INVITATION_EXPIRED, ROLE_NOT_FOUND, PERMISSION_NOT_FOUND,
                ORGANIZATION_NOT_FOUND, PROJECT_NOT_FOUND, PROJECT_MEMBER_NOT_FOUND,
                AI_PROVIDER_NOT_FOUND, AI_MODEL_NOT_FOUND, AI_SERVICE_CONFIG_NOT_FOUND, AI_VIDEO_TASK_NOT_FOUND,
                AI_VIDEO_RESULT_NOT_FOUND, AI_VIDEO_STORYBOARD_NOT_FOUND,
                AI_VOICE_TASK_NOT_FOUND, AI_VOICE_RESULT_NOT_FOUND,
                STORYBOARD_SUBTITLE_NOT_FOUND, SHOT_COMPOSE_TASK_NOT_FOUND,
                SHOT_COMPOSE_RESULT_NOT_FOUND, EPISODE_COMPOSE_TASK_NOT_FOUND,
                EPISODE_VIDEO_VERSION_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case ORGANIZATION_LEVEL_EXCEEDED, PROJECT_ARCHIVED, AI_VIDEO_TASK_STATUS_INVALID,
                AI_VOICE_TASK_STATUS_INVALID, SHOT_COMPOSE_TASK_STATUS_INVALID,
                EPISODE_COMPOSE_TASK_STATUS_INVALID -> HttpStatus.UNPROCESSABLE_ENTITY;
            case VALIDATION_ERROR, AI_PROVIDER_DISABLED, AI_PROVIDER_NOT_SUPPORTED, AI_MODEL_DISABLED,
                AI_AUTH_FAILED, AI_RATE_LIMIT, AI_QUOTA_EXCEEDED, AI_PROVIDER_TIMEOUT,
                AI_PROVIDER_ERROR, AI_RESPONSE_INVALID, AI_IMAGE_SERVICE_UNAVAILABLE, AI_VIDEO_SERVICE_UNAVAILABLE,
                AI_VIDEO_STORYBOARD_FIRST_FRAME_REQUIRED, AI_VOICE_SERVICE_UNAVAILABLE,
                SHOT_COMPOSE_STORYBOARD_VIDEO_REQUIRED, TEAM_POINTS_INSUFFICIENT -> HttpStatus.BAD_REQUEST;
        };
        return ResponseEntity
            .status(status)
            .body(ApiResponse.fail(exception.getErrorCode().name(), exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodArgumentNotValidException() {
        return ResponseEntity
            .badRequest()
            .body(ApiResponse.fail(ErrorCode.VALIDATION_ERROR.name(), "请求参数不正确。"));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolationException() {
        return ResponseEntity
            .badRequest()
            .body(ApiResponse.fail(ErrorCode.VALIDATION_ERROR.name(), "请求参数不正确。"));
    }
}
