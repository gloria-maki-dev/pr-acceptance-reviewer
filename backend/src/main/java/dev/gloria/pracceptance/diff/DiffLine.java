package dev.gloria.pracceptance.diff;

/**
 * Una línea del diff ya situada: de qué lado está, en qué número de línea y con qué contenido
 * (sin el prefijo {@code +}, {@code -} o espacio).
 */
public record DiffLine(DiffSide side, int lineNumber, String content) {}
