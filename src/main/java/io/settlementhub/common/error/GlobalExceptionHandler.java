package io.settlementhub.common.error;

import io.settlementhub.common.logging.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ProblemDetail> handleApiException(
            ApiException exception, HttpServletRequest request) {
        ErrorCode errorCode = exception.getErrorCode();
        return ResponseEntity.status(errorCode.status())
                .body(
                        problem(
                                errorCode.status(),
                                errorCode.code(),
                                exception.getMessage(),
                                request));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetail> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception, HttpServletRequest request) {
        List<Violation> violations =
                exception.getBindingResult().getFieldErrors().stream()
                        .map(GlobalExceptionHandler::toViolation)
                        .toList();
        ProblemDetail problem =
                problem(
                        HttpStatus.BAD_REQUEST,
                        ErrorCode.VALIDATION_FAILED.code(),
                        "Request validation failed.",
                        request);
        problem.setProperty("violations", violations);
        return ResponseEntity.badRequest().body(problem);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ProblemDetail> handleConstraintViolation(
            ConstraintViolationException exception, HttpServletRequest request) {
        List<Violation> violations =
                exception.getConstraintViolations().stream()
                        .map(
                                violation ->
                                        new Violation(
                                                violation.getPropertyPath().toString(),
                                                violation.getMessage()))
                        .toList();
        ProblemDetail problem =
                problem(
                        HttpStatus.BAD_REQUEST,
                        ErrorCode.VALIDATION_FAILED.code(),
                        "Request validation failed.",
                        request);
        problem.setProperty("violations", violations);
        return ResponseEntity.badRequest().body(problem);
    }

    @ExceptionHandler({
        HttpMessageNotReadableException.class,
        MethodArgumentTypeMismatchException.class,
        MissingServletRequestParameterException.class,
        MissingRequestHeaderException.class
    })
    ResponseEntity<ProblemDetail> handleMalformedRequest(
            Exception exception, HttpServletRequest request) {
        return ResponseEntity.badRequest()
                .body(
                        problem(
                                HttpStatus.BAD_REQUEST,
                                ErrorCode.BAD_REQUEST.code(),
                                "The request could not be read.",
                                request));
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ResponseEntity<ProblemDetail> handleOptimisticLock(
            OptimisticLockingFailureException exception, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(
                        problem(
                                HttpStatus.CONFLICT,
                                ErrorCode.CONFLICT.code(),
                                "The resource was modified concurrently. Retry the request.",
                                request));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ProblemDetail> handleDataIntegrity(
            DataIntegrityViolationException exception, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(
                        problem(
                                HttpStatus.CONFLICT,
                                ErrorCode.CONFLICT.code(),
                                "The request conflicts with existing data.",
                                request));
    }

    @ExceptionHandler(TaskRejectedException.class)
    ResponseEntity<ProblemDetail> handleTaskRejected(
            TaskRejectedException exception, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(
                        problem(
                                HttpStatus.TOO_MANY_REQUESTS,
                                ErrorCode.TOO_MANY_REQUESTS.code(),
                                "The service is busy. Retry the request later.",
                                request));
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ProblemDetail> handleAccessDenied(
            AccessDeniedException exception, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(
                        problem(
                                HttpStatus.FORBIDDEN,
                                ErrorCode.FORBIDDEN.code(),
                                "Access is denied.",
                                request));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> handleUnexpected(
            Exception exception, HttpServletRequest request) {
        log.error("Unhandled exception while processing request", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(
                        problem(
                                HttpStatus.INTERNAL_SERVER_ERROR,
                                ErrorCode.INTERNAL_ERROR.code(),
                                "An unexpected error occurred.",
                                request));
    }

    private static Violation toViolation(FieldError fieldError) {
        String message =
                fieldError.getDefaultMessage() == null
                        ? "is invalid"
                        : fieldError.getDefaultMessage();
        return new Violation(fieldError.getField(), message);
    }

    private static ProblemDetail problem(
            HttpStatus status, String code, String detail, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(status.getReasonPhrase());
        problem.setProperty("code", code);
        problem.setInstance(URI.create(request.getRequestURI()));
        String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
        if (correlationId != null) {
            problem.setProperty("correlationId", correlationId);
        }
        return problem;
    }

    public record Violation(String field, String message) {}
}
