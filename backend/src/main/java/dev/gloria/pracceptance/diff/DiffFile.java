package dev.gloria.pracceptance.diff;

import java.util.List;

/** The changes to one file within the diff. */
public record DiffFile(String path, List<DiffLine> lines) {

    public DiffFile {
        lines = List.copyOf(lines);
    }
}
