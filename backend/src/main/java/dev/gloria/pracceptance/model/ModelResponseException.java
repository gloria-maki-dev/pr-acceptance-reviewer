package dev.gloria.pracceptance.model;

/** El modelo respondió, pero su respuesta no tiene la forma que esperábamos. */
public class ModelResponseException extends RuntimeException {

    public ModelResponseException(String message) {
        super(message);
    }

    public ModelResponseException(String message, Throwable cause) {
        super(message, cause);
    }
}
