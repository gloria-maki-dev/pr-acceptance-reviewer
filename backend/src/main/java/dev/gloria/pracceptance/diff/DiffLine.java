package dev.gloria.pracceptance.diff;

/**
 * One line of the diff, placed: which side it is on, which line number it has, and its content
 * without the leading {@code +}, {@code -} or space.
 */
public record DiffLine(DiffSide side, int lineNumber, String content) {}
