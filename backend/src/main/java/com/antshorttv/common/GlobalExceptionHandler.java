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
            case FORBIDDEN, TENANT_DISABLED, MEMBER_REMOVED, OWNER_LEAVE_BLOCKED -> HttpStatus.FORBIDDEN;
            case DUPLICATE_MOBILE, DUPLICATE_PENDING_INVITATION, ALREADY_TENANT_MEMBER -> HttpStatus.CONFLICT;
            case NOT_FOUND, INVITATION_EXPIRED -> HttpStatus.NOT_FOUND;
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
