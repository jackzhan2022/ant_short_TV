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
                ORGANIZATION_HAS_MEMBERS, PROJECT_MEMBER_EXISTS -> HttpStatus.CONFLICT;
            case NOT_FOUND, INVITATION_EXPIRED, ROLE_NOT_FOUND, PERMISSION_NOT_FOUND,
                ORGANIZATION_NOT_FOUND, PROJECT_NOT_FOUND, PROJECT_MEMBER_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case ORGANIZATION_LEVEL_EXCEEDED, PROJECT_ARCHIVED -> HttpStatus.UNPROCESSABLE_ENTITY;
            case VALIDATION_ERROR -> HttpStatus.BAD_REQUEST;
        };
        return ResponseEntity
            .status(status)
            .body(ApiResponse.fail(exception.getErrorCode().name(), exception.getMessage()));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, ConstraintViolationException.class})
    public ResponseEntity<ApiResponse<Void>> handleValidationException(Exception exception) {
        return ResponseEntity
            .badRequest()
            .body(ApiResponse.fail(ErrorCode.VALIDATION_ERROR.name(), exception.getMessage()));
    }
}
