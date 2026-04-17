package com.platform.authz.modules.api;

import com.platform.authz.modules.domain.InvalidAllowedPrefixException;
import com.platform.authz.modules.domain.ModuleActiveKeyNotFoundException;
import com.platform.authz.modules.domain.ModuleAlreadyExistsException;
import com.platform.authz.modules.domain.ModuleConflictException;
import com.platform.authz.modules.domain.ModuleNotFoundException;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = {ModuleController.class, KeyController.class})
public class ModuleApiExceptionHandler {

    @ExceptionHandler({ModuleAlreadyExistsException.class, ModuleConflictException.class})
    public ResponseEntity<ProblemDetail> handleConflict(RuntimeException exception) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
        problemDetail.setTitle("Module conflict");
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problemDetail);
    }

    @ExceptionHandler(ModuleNotFoundException.class)
    public ResponseEntity<ProblemDetail> handleNotFound(ModuleNotFoundException exception) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
        problemDetail.setTitle("Module not found");
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problemDetail);
    }

    @ExceptionHandler({InvalidAllowedPrefixException.class, ModuleActiveKeyNotFoundException.class})
    public ResponseEntity<ProblemDetail> handleUnprocessable(RuntimeException exception) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNPROCESSABLE_ENTITY,
                exception.getMessage()
        );
        problemDetail.setTitle("Module request rejected");
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(problemDetail);
    }

    @ExceptionHandler(PlatformAdminRequiredException.class)
    public ResponseEntity<ProblemDetail> handleForbidden(PlatformAdminRequiredException exception) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, exception.getMessage());
        problemDetail.setTitle("Forbidden");
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(problemDetail);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException exception) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "Request validation failed"
        );
        problemDetail.setTitle("Validation error");
        problemDetail.setProperty(
                "errors",
                exception.getBindingResult().getFieldErrors().stream()
                        .collect(Collectors.toMap(
                                fieldError -> fieldError.getField(),
                                fieldError -> fieldError.getDefaultMessage() != null
                                        ? fieldError.getDefaultMessage()
                                        : "Invalid value",
                                (first, ignored) -> first
                        ))
        );
        return ResponseEntity.badRequest().body(problemDetail);
    }
}
