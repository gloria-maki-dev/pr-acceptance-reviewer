package dev.gloria.pracceptance.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/** El error tal y como lo recibe la pantalla: un mensaje para enseñar y, si los hay, detalles. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(String error, List<String> details) {

    public ApiError(String error) {
        this(error, null);
    }
}
