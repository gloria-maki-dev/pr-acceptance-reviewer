package dev.gloria.pracceptance.review;

import dev.gloria.pracceptance.diff.DiffSide;

/**
 * The citation backing a verdict: file, side of the diff, line number and the snippet of text.
 */
public record Evidence(String file, DiffSide side, int lineNumber, String snippet) {}
