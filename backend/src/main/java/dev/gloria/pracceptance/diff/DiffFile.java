package dev.gloria.pracceptance.diff;

import java.util.List;

/** Los cambios de un archivo dentro del diff. */
public record DiffFile(String path, List<DiffLine> lines) {

    public DiffFile {
        lines = List.copyOf(lines);
    }
}
