package dev.gloria.pracceptance.diff;

import java.util.List;
import java.util.Optional;

/**
 * A unified diff, parsed.
 *
 * <p>This is the source of truth every citation from the model is checked against: a citation
 * that does not point at a line held here turns its criterion into "Not verified".
 */
public record ParsedDiff(List<DiffFile> files) {

    public ParsedDiff {
        files = List.copyOf(files);
    }

    /** Looks a file up by path, tolerating the {@code a/} and {@code b/} prefixes of the diff. */
    public Optional<DiffFile> file(String path) {
        String wanted = normalisePath(path);
        return files.stream().filter(f -> normalisePath(f.path()).equals(wanted)).findFirst();
    }

    /** Compares paths without the diff's prefixes. Also used by {@link DiffParser} when grouping. */
    static String normalisePath(String path) {
        String cleaned = path == null ? "" : path.strip().replace('\\', '/');
        if (cleaned.startsWith("a/") || cleaned.startsWith("b/")) {
            cleaned = cleaned.substring(2);
        }
        while (cleaned.startsWith("./")) {
            cleaned = cleaned.substring(2);
        }
        return cleaned;
    }

    /**
     * Renders the diff with the file, side and line number in front of every line.
     *
     * <p>This is what the model is shown. When all it sees are lines that are already labelled,
     * citing one correctly is easier than making a reference up.
     */
    public String toAnnotatedText() {
        StringBuilder out = new StringBuilder();
        for (DiffFile file : files) {
            out.append("FILE: ").append(file.path()).append('\n');
            for (DiffLine line : file.lines()) {
                out.append('[')
                        .append(line.side())
                        .append(' ')
                        .append(line.lineNumber())
                        .append("] ")
                        .append(line.content())
                        .append('\n');
            }
            out.append('\n');
        }
        return out.toString();
    }
}
