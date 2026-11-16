package dev.gloria.pracceptance.diff;

/** El texto recibido no se puede interpretar como un diff unificado. */
public class InvalidDiffException extends RuntimeException {

    public InvalidDiffException(String message) {
        super(message);
    }
}
