package dev.gloria.pracceptance.model;

/** The model answered, but its answer is not the shape we expected. */
public class ModelResponseException extends RuntimeException {

    public ModelResponseException(String message) {
        super(message);
    }

    public ModelResponseException(String message, Throwable cause) {
        super(message, cause);
    }
}
