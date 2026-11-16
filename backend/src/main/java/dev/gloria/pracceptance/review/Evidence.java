package dev.gloria.pracceptance.review;

import dev.gloria.pracceptance.diff.DiffSide;

/**
 * La cita con la que el modelo respalda un veredicto: archivo, lado del diff, número de línea y el
 * fragmento de texto.
 */
public record Evidence(String file, DiffSide side, int lineNumber, String snippet) {}
