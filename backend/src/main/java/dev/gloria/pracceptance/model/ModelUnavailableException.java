package dev.gloria.pracceptance.model;

/** No se pudo llegar al modelo: falta la credencial, o la API no respondió. */
public class ModelUnavailableException extends RuntimeException {

    public ModelUnavailableException(String message) {
        super(message);
    }

    public ModelUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
