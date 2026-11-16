package dev.gloria.pracceptance.diff;

/** The text received cannot be read as a unified diff. */
public class InvalidDiffException extends RuntimeException {

    public InvalidDiffException(String message) {
        super(message);
    }
}
