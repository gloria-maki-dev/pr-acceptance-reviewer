package dev.gloria.pracceptance.review;

/** Veredicto de un criterio de aceptación. */
public enum ReviewStatus {
    /** Cumplido: el diff lo cumple y la cita que lo respalda está verificada. */
    MET,
    /** No cumplido: el diff lo incumple y la cita que lo demuestra está verificada. */
    NOT_MET,
    /**
     * No verificado: el diff no da contexto suficiente, o la cita del modelo no se corresponde con
     * ninguna línea del diff. Es el estado al que se degrada cualquier veredicto que no se pueda
     * comprobar.
     */
    UNVERIFIED
}
