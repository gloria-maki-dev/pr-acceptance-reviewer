package dev.gloria.pracceptance.diff;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Reads a unified diff (the output of {@code git diff}) and places every line in its file, its
 * side and its line number.
 *
 * <p>Each hunk header declares how many lines it contributes per side, and this parser counts
 * them down. That is not a detail: inside a hunk, a removed line whose content starts with
 * {@code -- } (a SQL comment, say) is written {@code --- ...} and is indistinguishable from a
 * file header. Without the countdown the rest of the hunk would be dropped in silence, and a
 * criterion of the "this must not change" kind would come back Met over a mutilated diff.
 *
 * <p>It is tolerant of whatever surrounds the changes - {@code git} headers, file modes, index
 * lines - and strict about what matters: if it finds no changed file at all, it fails.
 */
@Component
public class DiffParser {

    private static final Pattern HUNK_HEADER =
            Pattern.compile("^@@ -(\\d+)(?:,(\\d+))? \\+(\\d+)(?:,(\\d+))? @@.*$");

    public ParsedDiff parse(String rawDiff) {
        if (rawDiff == null || rawDiff.isBlank()) {
            throw new InvalidDiffException("The diff is empty.");
        }

        List<DiffFile> files = new ArrayList<>();
        String currentPath = null;
        List<DiffLine> currentLines = new ArrayList<>();
        int oldLine = 0;
        int newLine = 0;
        int oldRemaining = 0;
        int newRemaining = 0;

        String[] rawLines = rawDiff.split("\n", -1);
        // A file ending in a newline leaves a trailing empty element that is not a line of the
        // diff; taking it seriously would invent a context line.
        int lineCount = rawLines.length;
        if (lineCount > 0 && rawLines[lineCount - 1].isEmpty()) {
            lineCount--;
        }

        for (int i = 0; i < lineCount; i++) {
            String line = stripCarriageReturn(rawLines[i]);
            boolean insideHunk = oldRemaining > 0 || newRemaining > 0;

            if (!insideHunk) {
                if (line.startsWith("diff --git ")) {
                    flush(files, currentPath, currentLines);
                    currentLines = new ArrayList<>();
                    currentPath = pathFromGitHeader(line);
                    continue;
                }

                if (line.startsWith("--- ")) {
                    // A new file starts here, even without a "diff --git" header.
                    flush(files, currentPath, currentLines);
                    currentLines = new ArrayList<>();
                    String oldPath = stripPathPrefix(line.substring(4));
                    currentPath = isDevNull(oldPath) ? null : oldPath;
                    continue;
                }

                if (line.startsWith("+++ ")) {
                    // On a deletion, "+++ /dev/null" leaves the old side's path standing.
                    String newPath = stripPathPrefix(line.substring(4));
                    if (!isDevNull(newPath)) {
                        currentPath = newPath;
                    }
                    continue;
                }

                Matcher hunk = HUNK_HEADER.matcher(line);
                if (hunk.matches()) {
                    oldLine = Integer.parseInt(hunk.group(1));
                    oldRemaining = hunk.group(2) == null ? 1 : Integer.parseInt(hunk.group(2));
                    newLine = Integer.parseInt(hunk.group(3));
                    newRemaining = hunk.group(4) == null ? 1 : Integer.parseInt(hunk.group(4));
                }
                // Anything else outside a hunk (index, modes, loose text) is ignored.
                continue;
            }

            if (line.startsWith("\\")) {
                // "\ No newline at end of file" is not a line of the file and spends no budget.
                continue;
            }

            if (line.startsWith("+")) {
                addLine(currentLines, currentPath, DiffSide.ADDED, newLine++, line.substring(1));
                newRemaining = Math.max(0, newRemaining - 1);
            } else if (line.startsWith("-")) {
                addLine(currentLines, currentPath, DiffSide.REMOVED, oldLine++, line.substring(1));
                oldRemaining = Math.max(0, oldRemaining - 1);
            } else if (line.startsWith(" ") || line.isEmpty()) {
                // A blank line inside a hunk is an empty context line.
                String content = line.isEmpty() ? "" : line.substring(1);
                addLine(currentLines, currentPath, DiffSide.CONTEXT, newLine++, content);
                oldLine++;
                oldRemaining = Math.max(0, oldRemaining - 1);
                newRemaining = Math.max(0, newRemaining - 1);
            } else {
                // Junk where a hunk line should be: treat the hunk as closed.
                oldRemaining = 0;
                newRemaining = 0;
            }
        }

        flush(files, currentPath, currentLines);

        if (files.isEmpty()) {
            throw new InvalidDiffException(
                    "No changed files found. Paste the output of `git diff`, including its "
                            + "`--- a/file`, `+++ b/file` and `@@` headers.");
        }
        return new ParsedDiff(files);
    }

    private static void addLine(List<DiffLine> lines, String path, DiffSide side, int number, String content) {
        if (path != null) {
            lines.add(new DiffLine(side, number, content));
        }
    }

    /**
     * Closes the file currently being read.
     *
     * <p>If that same path appeared before - two commits pasted together, a {@code git log -p} -
     * the lines are merged into a single file. Kept apart, only a citation to the first block
     * could be verified, and a correct citation to the second one would be rejected.
     */
    private static void flush(List<DiffFile> files, String path, List<DiffLine> lines) {
        if (path == null || lines.isEmpty()) {
            return;
        }
        String key = ParsedDiff.normalisePath(path);
        for (int i = 0; i < files.size(); i++) {
            DiffFile existing = files.get(i);
            if (ParsedDiff.normalisePath(existing.path()).equals(key)) {
                List<DiffLine> merged = new ArrayList<>(existing.lines());
                merged.addAll(lines);
                files.set(i, new DiffFile(existing.path(), merged));
                return;
            }
        }
        files.add(new DiffFile(path, List.copyOf(lines)));
    }

    private static boolean isDevNull(String path) {
        return "/dev/null".equals(path) || "dev/null".equals(path);
    }

    private static String pathFromGitHeader(String header) {
        // diff --git a/path b/path
        String rest = header.substring("diff --git ".length()).strip();
        int split = rest.lastIndexOf(" b/");
        if (split > 0) {
            return stripPathPrefix(rest.substring(split + 1));
        }
        return null;
    }

    private static String stripPathPrefix(String rawPath) {
        String path = rawPath.strip();
        // Headers may carry a tab and a timestamp after the path.
        int tab = path.indexOf('\t');
        if (tab >= 0) {
            path = path.substring(0, tab).strip();
        }
        if (path.length() >= 2 && path.startsWith("\"") && path.endsWith("\"")) {
            // git quotes paths with non-ASCII characters when core.quotePath is on.
            path = unquoteGitPath(path.substring(1, path.length() - 1));
        }
        if (path.startsWith("a/") || path.startsWith("b/")) {
            path = path.substring(2);
        }
        return path;
    }

    /** Undoes the octal escaping git applies to a quoted path. */
    private static String unquoteGitPath(String quoted) {
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        for (int i = 0; i < quoted.length(); i++) {
            char c = quoted.charAt(i);
            if (c == '\\' && i + 3 < quoted.length() && isOctal(quoted, i + 1)) {
                bytes.write(Integer.parseInt(quoted.substring(i + 1, i + 4), 8));
                i += 3;
            } else if (c == '\\' && i + 1 < quoted.length()) {
                bytes.write(quoted.charAt(++i));
            } else {
                byte[] encoded = String.valueOf(c).getBytes(java.nio.charset.StandardCharsets.UTF_8);
                bytes.write(encoded, 0, encoded.length);
            }
        }
        return new String(bytes.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
    }

    private static boolean isOctal(String text, int from) {
        for (int i = from; i < from + 3; i++) {
            if (text.charAt(i) < '0' || text.charAt(i) > '7') {
                return false;
            }
        }
        return true;
    }

    private static String stripCarriageReturn(String line) {
        return line.endsWith("\r") ? line.substring(0, line.length() - 1) : line;
    }
}
