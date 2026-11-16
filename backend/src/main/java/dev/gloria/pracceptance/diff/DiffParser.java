package dev.gloria.pracceptance.diff;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Interpreta un diff unificado (la salida de {@code git diff}) y sitúa cada línea en su archivo,
 * su lado y su número de línea.
 *
 * <p>La cabecera de cada fragmento declara cuántas líneas trae de cada lado, y este parser lleva
 * la cuenta. Eso no es un detalle: dentro de un fragmento, una línea eliminada cuyo contenido
 * empiece por {@code -- } (un comentario de SQL, por ejemplo) se escribe {@code --- …} y es
 * idéntica a una cabecera de archivo. Sin llevar la cuenta, el resto del fragmento se perdería en
 * silencio y un criterio del tipo «esto no debe cambiar» saldría Cumplido sobre un diff mutilado.
 *
 * <p>Es tolerante con lo que rodea a los cambios —cabeceras de {@code git}, modos, índices— y
 * estricto con lo que importa: si no encuentra ni un archivo con cambios, falla.
 */
@Component
public class DiffParser {

    private static final Pattern HUNK_HEADER =
            Pattern.compile("^@@ -(\\d+)(?:,(\\d+))? \\+(\\d+)(?:,(\\d+))? @@.*$");

    public ParsedDiff parse(String rawDiff) {
        if (rawDiff == null || rawDiff.isBlank()) {
            throw new InvalidDiffException("El diff está vacío.");
        }

        List<DiffFile> files = new ArrayList<>();
        String currentPath = null;
        List<DiffLine> currentLines = new ArrayList<>();
        int oldLine = 0;
        int newLine = 0;
        int oldRemaining = 0;
        int newRemaining = 0;

        String[] rawLines = rawDiff.split("\n", -1);
        // Un archivo que termina en salto de línea deja un último elemento vacío que no es
        // ninguna línea del diff; si lo tomáramos en serio, inventaríamos una línea de contexto.
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
                    // Empieza un archivo nuevo, aunque no haya cabecera «diff --git».
                    flush(files, currentPath, currentLines);
                    currentLines = new ArrayList<>();
                    String oldPath = stripPathPrefix(line.substring(4));
                    currentPath = isDevNull(oldPath) ? null : oldPath;
                    continue;
                }

                if (line.startsWith("+++ ")) {
                    // En un borrado, «+++ /dev/null» deja en pie la ruta del lado antiguo.
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
                // Cualquier otra cosa fuera de un fragmento (index, modos, texto suelto) se ignora.
                continue;
            }

            if (line.startsWith("\\")) {
                // «\ No newline at end of file»: no es una línea del archivo y no gasta cupo.
                continue;
            }

            if (line.startsWith("+")) {
                addLine(currentLines, currentPath, DiffSide.ADDED, newLine++, line.substring(1));
                newRemaining = Math.max(0, newRemaining - 1);
            } else if (line.startsWith("-")) {
                addLine(currentLines, currentPath, DiffSide.REMOVED, oldLine++, line.substring(1));
                oldRemaining = Math.max(0, oldRemaining - 1);
            } else if (line.startsWith(" ") || line.isEmpty()) {
                // Una línea en blanco dentro de un fragmento es una línea de contexto vacía.
                String content = line.isEmpty() ? "" : line.substring(1);
                addLine(currentLines, currentPath, DiffSide.CONTEXT, newLine++, content);
                oldLine++;
                oldRemaining = Math.max(0, oldRemaining - 1);
                newRemaining = Math.max(0, newRemaining - 1);
            } else {
                // Basura donde debería haber una línea del fragmento: damos el fragmento por cerrado.
                oldRemaining = 0;
                newRemaining = 0;
            }
        }

        flush(files, currentPath, currentLines);

        if (files.isEmpty()) {
            throw new InvalidDiffException(
                    "No reconozco ningún archivo con cambios. Pega la salida de «git diff», "
                            + "con sus cabeceras «--- a/archivo», «+++ b/archivo» y «@@».");
        }
        return new ParsedDiff(files);
    }

    private static void addLine(List<DiffLine> lines, String path, DiffSide side, int number, String content) {
        if (path != null) {
            lines.add(new DiffLine(side, number, content));
        }
    }

    /**
     * Cierra el archivo en curso.
     *
     * <p>Si esa misma ruta ya apareció antes —dos commits pegados, un {@code git log -p}— se
     * juntan las líneas en un solo archivo. Si se guardaran por separado, solo se podría verificar
     * una cita al primer bloque, y una cita correcta al segundo se rechazaría.
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
        // diff --git a/ruta b/ruta
        String rest = header.substring("diff --git ".length()).strip();
        int split = rest.lastIndexOf(" b/");
        if (split > 0) {
            return stripPathPrefix(rest.substring(split + 1));
        }
        return null;
    }

    private static String stripPathPrefix(String rawPath) {
        String path = rawPath.strip();
        // Las cabeceras pueden traer un tabulador con la marca de tiempo detrás de la ruta.
        int tab = path.indexOf('\t');
        if (tab >= 0) {
            path = path.substring(0, tab).strip();
        }
        if (path.length() >= 2 && path.startsWith("\"") && path.endsWith("\"")) {
            // git entrecomilla las rutas con caracteres no ASCII cuando core.quotePath está activo.
            path = unquoteGitPath(path.substring(1, path.length() - 1));
        }
        if (path.startsWith("a/") || path.startsWith("b/")) {
            path = path.substring(2);
        }
        return path;
    }

    /** Deshace los escapes octales con los que git entrecomilla una ruta. */
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
