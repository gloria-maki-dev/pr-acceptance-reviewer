package dev.gloria.pracceptance.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/** An error as the screen receives it: a message to show and, when there are any, details. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(String error, List<String> details) {

    public ApiError(String error) {
        this(error, null);
    }
}
