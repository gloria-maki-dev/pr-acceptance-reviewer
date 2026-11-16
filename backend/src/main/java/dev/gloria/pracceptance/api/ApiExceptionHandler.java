package dev.gloria.pracceptance.api;

import dev.gloria.pracceptance.diff.InvalidDiffException;
import dev.gloria.pracceptance.model.ModelResponseException;
import dev.gloria.pracceptance.model.ModelUnavailableException;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Turns failures into something the screen can show.
 *
 * <p>Every cause gets its own code: what the reviewer sent (400), what the model returned (502),
 * and what is still unconfigured (503). That way the screen can say what happened without
 * guessing.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> onValidation(MethodArgumentNotValidException e) {
        List<String> details = e.getBindingResult().getAllErrors().stream()
                .map(error -> error.getDefaultMessage() == null ? "Invalid value." : error.getDefaultMessage())
                .distinct()
                .toList();
        String message = details.isEmpty() ? "The request is not valid." : String.join(" ", details);
        return ResponseEntity.badRequest().body(new ApiError(message, details));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> onConstraintViolation(ConstraintViolationException e) {
        return ResponseEntity.badRequest().body(new ApiError(e.getMessage()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> onUnreadableBody(HttpMessageNotReadableException e) {
        return ResponseEntity.badRequest()
                .body(new ApiError("Could not read the request body: JSON was expected."));
    }

    @ExceptionHandler(InvalidDiffException.class)
    public ResponseEntity<ApiError> onInvalidDiff(InvalidDiffException e) {
        return ResponseEntity.badRequest().body(new ApiError(e.getMessage()));
    }

    @ExceptionHandler(ModelResponseException.class)
    public ResponseEntity<ApiError> onModelResponse(ModelResponseException e) {
        log.warn("Unusable answer from the model: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(new ApiError(e.getMessage()));
    }

    @ExceptionHandler(ModelUnavailableException.class)
    public ResponseEntity<ApiError> onModelUnavailable(ModelUnavailableException e) {
        log.warn("Could not reach the model: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(new ApiError(e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> onUnexpected(Exception e) {
        // Spring MVC signals the wrong method or content type with exceptions that already carry
        // their status (405, 415, 406...). Without this, the catch-all would swallow them and
        // every one would come out as a 500 with a stack trace in the log.
        if (e instanceof ErrorResponse errorResponse) {
            HttpStatusCode status = errorResponse.getStatusCode();
            log.debug("Request rejected by Spring MVC with {}: {}", status, e.getMessage());
            return ResponseEntity.status(status).body(new ApiError(describe(status)));
        }
        log.error("Unexpected failure while reviewing", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiError("Unexpected server failure."));
    }

    private static String describe(HttpStatusCode status) {
        if (status.isSameCodeAs(HttpStatus.METHOD_NOT_ALLOWED)) {
            return "This resource only accepts POST.";
        }
        if (status.isSameCodeAs(HttpStatus.UNSUPPORTED_MEDIA_TYPE)) {
            return "The body must be sent as application/json.";
        }
        return "The request is not valid.";
    }
}
