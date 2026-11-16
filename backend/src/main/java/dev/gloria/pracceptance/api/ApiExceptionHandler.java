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
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Traduce los fallos a algo que la pantalla pueda enseñar.
 *
 * <p>Cada causa tiene su código: lo que mandó quien revisa (400), lo que devolvió el modelo (502) y
 * lo que falta por configurar (503). Así la pantalla puede decir qué pasó sin adivinarlo.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> onValidation(MethodArgumentNotValidException e) {
        List<String> details = e.getBindingResult().getAllErrors().stream()
                .map(error -> error.getDefaultMessage() == null ? "Valor no válido." : error.getDefaultMessage())
                .distinct()
                .toList();
        String message = details.isEmpty() ? "La petición no es válida." : String.join(" ", details);
        return ResponseEntity.badRequest().body(new ApiError(message, details));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> onConstraintViolation(ConstraintViolationException e) {
        return ResponseEntity.badRequest().body(new ApiError(e.getMessage()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> onUnreadableBody(HttpMessageNotReadableException e) {
        return ResponseEntity.badRequest()
                .body(new ApiError("No entiendo el cuerpo de la petición: se esperaba JSON."));
    }

    @ExceptionHandler(InvalidDiffException.class)
    public ResponseEntity<ApiError> onInvalidDiff(InvalidDiffException e) {
        return ResponseEntity.badRequest().body(new ApiError(e.getMessage()));
    }

    @ExceptionHandler(ModelResponseException.class)
    public ResponseEntity<ApiError> onModelResponse(ModelResponseException e) {
        log.warn("Respuesta inservible del modelo: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(new ApiError(e.getMessage()));
    }

    @ExceptionHandler(ModelUnavailableException.class)
    public ResponseEntity<ApiError> onModelUnavailable(ModelUnavailableException e) {
        log.warn("No se pudo llegar al modelo: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(new ApiError(e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> onUnexpected(Exception e) {
        // Spring MVC señala el método o el tipo de contenido equivocados con excepciones que ya
        // traen su código (405, 415, 406...). Sin esto, el manejador genérico se las tragaría y
        // todas saldrían como un 500 con su traza en el log.
        if (e instanceof ErrorResponse errorResponse) {
            HttpStatusCode status = errorResponse.getStatusCode();
            log.debug("Petición rechazada por Spring MVC con {}: {}", status, e.getMessage());
            return ResponseEntity.status(status).body(new ApiError(describe(status)));
        }
        log.error("Fallo inesperado al revisar", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiError("Fallo inesperado en el servidor."));
    }

    private static String describe(HttpStatusCode status) {
        if (status.isSameCodeAs(HttpStatus.METHOD_NOT_ALLOWED)) {
            return "Este recurso solo acepta POST.";
        }
        if (status.isSameCodeAs(HttpStatus.UNSUPPORTED_MEDIA_TYPE)) {
            return "El cuerpo tiene que ir como application/json.";
        }
        return "La petición no es válida.";
    }
}
