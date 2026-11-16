package dev.gloria.pracceptance.diff;

/** Which side of the diff a line belongs to. */
public enum DiffSide {
    /** An added line ({@code +} prefix); numbered against the new file. */
    ADDED,
    /** A removed line ({@code -} prefix); numbered against the old file. */
    REMOVED,
    /** A context line, identical on both sides; numbered against the new file. */
    CONTEXT
}
