package dev.gloria.pracceptance.diff;

import java.util.List;
import java.util.Optional;

/**
 * Un diff unificado ya interpretado.
 *
 * <p>Esta es la fuente de verdad contra la que se comprueban las citas del modelo: si una cita no
 * apunta a una línea que esté aquí, el criterio pasa a «No verificado».
 */
public record ParsedDiff(List<DiffFile> files) {

    public ParsedDiff {
        files = List.copyOf(files);
    }

    /** Busca un archivo por ruta, tolerando los prefijos {@code a/} y {@code b/} del diff. */
    public Optional<DiffFile> file(String path) {
        String wanted = normalisePath(path);
        return files.stream().filter(f -> normalisePath(f.path()).equals(wanted)).findFirst();
    }

    /** Compara rutas sin los prefijos del diff. Lo usa también {@link DiffParser} al agrupar. */
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
     * Rinde el diff con el archivo, el lado y el número de línea delante de cada línea.
     *
     * <p>Es lo que se le manda al modelo: si lo único que ve son líneas ya etiquetadas, citar bien
     * le resulta más fácil que inventarse una referencia.
     */
    public String toAnnotatedText() {
        StringBuilder out = new StringBuilder();
        for (DiffFile file : files) {
            out.append("ARCHIVO: ").append(file.path()).append('\n');
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
