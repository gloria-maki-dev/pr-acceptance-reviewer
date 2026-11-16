package dev.gloria.pracceptance.model;

/** The model could not be reached: the credential is missing, or the API did not answer. */
public class ModelUnavailableException extends RuntimeException {

    public ModelUnavailableException(String message) {
        super(message);
    }

    public ModelUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
