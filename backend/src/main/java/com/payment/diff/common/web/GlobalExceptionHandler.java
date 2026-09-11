package com.payment.diff.common.web;

import com.payment.diff.common.api.ApiResponse;
import com.payment.diff.common.exception.BusinessException;
import com.payment.diff.common.exception.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 全局异常处理器。
 * 约定：业务错误返回 HTTP 200 + 业务码；仅鉴权失败返回 401，未捕获系统异常返回 500。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 业务异常：使用业务错误码，HTTP 200 */
    @ExceptionHandler(BusinessException.class)
    public ApiResponse<Void> handleBusiness(BusinessException e) {
        return ApiResponse.fail(e.getErrorCode().getCode(), e.getMessage());
    }

    /** 鉴权失败：HTTP 401 */
    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnauthorized(UnauthorizedException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.fail(ErrorCode.UNAUTHORIZED.getCode(), e.getMessage()));
    }

    /** @Valid 请求体校验失败 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ApiResponse<Void> handleValid(MethodArgumentNotValidException e) {
        String detail = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + " " + f.getDefaultMessage())
                .findFirst()
                .orElse(ErrorCode.PARAM_INVALID.getMessage());
        return ApiResponse.fail(ErrorCode.PARAM_INVALID.getCode(), detail);
    }

    /** 表单绑定校验失败 */
    @ExceptionHandler(BindException.class)
    public ApiResponse<Void> handleBind(BindException e) {
        String detail = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + " " + f.getDefaultMessage())
                .findFirst()
                .orElse(ErrorCode.PARAM_INVALID.getMessage());
        return ApiResponse.fail(ErrorCode.PARAM_INVALID.getCode(), detail);
    }

    /** @Validated 参数级校验失败 */
    @ExceptionHandler(ConstraintViolationException.class)
    public ApiResponse<Void> handleConstraint(ConstraintViolationException e) {
        String detail = e.getConstraintViolations().stream()
                .map(v -> v.getPropertyPath() + " " + v.getMessage())
                .findFirst()
                .orElse(ErrorCode.PARAM_INVALID.getMessage());
        return ApiResponse.fail(ErrorCode.PARAM_INVALID.getCode(), detail);
    }

    /** 请求体不可读 / 参数类型不匹配：均视为参数错误 */
    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ApiResponse<Void> handleUnreadable(Exception e) {
        return ApiResponse.fail(ErrorCode.PARAM_INVALID.getCode(), ErrorCode.PARAM_INVALID.getMessage());
    }

    /** 静态资源/路由不存在：按参数错误处理，避免落入 500 */
    @ExceptionHandler(NoResourceFoundException.class)
    public ApiResponse<Void> handleNoResource(NoResourceFoundException e) {
        return ApiResponse.fail(ErrorCode.PARAM_INVALID.getCode(), "接口不存在");
    }

    /** 兜底：系统异常，HTTP 500 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnknown(Exception e) {
        log.error("未捕获系统异常", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.fail(ErrorCode.SYSTEM_ERROR.getCode(), ErrorCode.SYSTEM_ERROR.getMessage()));
    }
}
