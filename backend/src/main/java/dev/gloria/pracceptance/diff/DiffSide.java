package dev.gloria.pracceptance.diff;

/** Lado del diff al que pertenece una línea. */
public enum DiffSide {
    /** Línea añadida (prefijo {@code +}); se numera con el archivo nuevo. */
    ADDED,
    /** Línea eliminada (prefijo {@code -}); se numera con el archivo antiguo. */
    REMOVED,
    /** Línea de contexto, igual en los dos lados; se numera con el archivo nuevo. */
    CONTEXT
}
